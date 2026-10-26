package com.optimisante.backend.domain.catalog.service;

import com.optimisante.backend.domain.catalog.entity.MotifMouvementStock;
import com.optimisante.backend.domain.catalog.entity.MouvementStock;
import com.optimisante.backend.domain.catalog.entity.Product;
import com.optimisante.backend.domain.catalog.repository.MouvementStockRepository;
import com.optimisante.backend.domain.catalog.repository.ProductRepository;
import com.optimisante.backend.domain.identity.entity.User;
import com.optimisante.backend.domain.identity.repository.UserRepository;
import com.optimisante.backend.domain.notification.entity.NotificationSeverity;
import com.optimisante.backend.domain.notification.service.NotificationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Le passage obligé de toute variation de stock.
 *
 * <p>Avant ce service, le stock se modifiait à deux endroits sans se parler :
 * {@code OrderService} décrémentait au paiement, l'administration réécrivait la quantité
 * depuis la fiche produit. Aucun des deux ne laissait de trace, et le second écrasait
 * silencieusement le premier — un inventaire saisi pendant qu'une commande se réglait perdait
 * la vente. Tout passe désormais par {@link #appliquer}, qui verrouille la ligne, écrit le
 * journal et prévient quand le seuil critique est franchi.</p>
 *
 * <p><b>Le verrou n'est pas optionnel.</b> Deux ventes simultanées de la même référence lisent
 * sinon le même stock de départ et écrivent le même stock d'arrivée : une unité disparaît du
 * compte sans disparaître de l'entrepôt. Le verrou pessimiste les met en file, au prix d'une
 * attente de quelques millisecondes.</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class StockService {

    private final ProductRepository productRepository;
    private final MouvementStockRepository mouvementRepository;
    private final UserRepository userRepository;
    private final NotificationService notifications;

    /** Un stock ne descend jamais sous zéro : un négatif serait une erreur comptable visible. */
    private static final int PLANCHER = 0;

    /**
     * Applique une variation et journalise. Renvoie le stock obtenu.
     *
     * @param delta variation signée ; une valeur nulle n'écrit rien et ne notifie pas.
     */
    @Transactional
    public int appliquer(UUID productId, int delta, MotifMouvementStock motif,
                         UUID orderId, UUID auteurId, String commentaire) {
        if (delta == 0) {
            return lireStock(productId);
        }

        Product produit = productRepository.findByIdWithPessimisticLock(productId)
                .orElseThrow(() -> new IllegalArgumentException("Produit introuvable : " + productId));

        int avant = produit.getStockQuantity() == null ? 0 : produit.getStockQuantity();
        int apres = Math.max(PLANCHER, avant + delta);

        // Le delta journalisé est celui RÉELLEMENT appliqué : quand le plancher rogne une
        // sortie, écrire la quantité demandée ferait mentir la somme des mouvements, qui doit
        // toujours égaler le stock courant. C'est cette égalité qui rend le journal vérifiable.
        int deltaReel = apres - avant;
        if (deltaReel != delta) {
            log.warn("Stock plancher atteint sur {} : demande {} ramenee a {}", productId, delta, deltaReel);
        }
        if (deltaReel == 0) {
            return avant;
        }

        produit.setStockQuantity(apres);
        if (motif == MotifMouvementStock.INVENTAIRE) {
            produit.setStockInventorieLe(OffsetDateTime.now());
        }
        productRepository.save(produit);

        mouvementRepository.save(MouvementStock.builder()
                .product(produit)
                .delta(deltaReel)
                .stockApres(apres)
                .motif(motif)
                .orderId(orderId)
                .auteur(auteurId == null ? null : userRepository.findById(auteurId).orElse(null))
                .commentaire(commentaire)
                .build());

        signalerSiCritique(produit, avant, apres);
        return apres;
    }

    /**
     * Pose le stock à la valeur constatée lors d'un comptage, plutôt que d'appliquer un écart.
     *
     * <p>C'est la forme naturelle d'un inventaire : on compte 12 unités, on saisit 12. Calculer
     * l'écart à la main avant de le saisir est le meilleur moyen de se tromper de signe.</p>
     */
    @Transactional
    public int inventorier(UUID productId, int quantiteConstatee, UUID auteurId, String commentaire) {
        if (quantiteConstatee < 0) {
            throw new IllegalArgumentException("Une quantité comptée ne peut pas être négative.");
        }
        int ecart = quantiteConstatee - lireStock(productId);
        if (ecart == 0) {
            // Compter et retrouver le même chiffre reste une information : c'est elle qui fait
            // passer le produit de « jamais inventorié » à « vérifié ce jour ». Sans ce cas,
            // confirmer un stock juste n'aurait aucun effet visible.
            return confirmerSansEcart(productId, auteurId, commentaire);
        }
        return appliquer(productId, ecart, MotifMouvementStock.INVENTAIRE, null, auteurId, commentaire);
    }

    /** Sortie liée à une commande payée. Appelé par {@code OrderService}. */
    @Transactional
    public int sortieVente(UUID productId, int quantite, UUID orderId) {
        return appliquer(productId, -Math.abs(quantite), MotifMouvementStock.VENTE, orderId, null, null);
    }

    /** Retour en stock après annulation ou remboursement. */
    @Transactional
    public int retourAnnulation(UUID productId, int quantite, UUID orderId, UUID auteurId) {
        return appliquer(productId, Math.abs(quantite), MotifMouvementStock.ANNULATION, orderId, auteurId, null);
    }

    // ------------------------------------------------------------------ interne

    /**
     * Sans {@code @Transactional} : la méthode n'est appelée que depuis {@link #inventorier},
     * déjà transactionnel. L'annoter ici serait trompeur — Spring passe par un proxy, qu'un
     * appel interne ne traverse pas : elle n'aurait aucun effet.
     */
    private int confirmerSansEcart(UUID productId, UUID auteurId, String commentaire) {
        Product produit = productRepository.findByIdWithPessimisticLock(productId)
                .orElseThrow(() -> new IllegalArgumentException("Produit introuvable : " + productId));
        int stock = produit.getStockQuantity() == null ? 0 : produit.getStockQuantity();

        produit.setStockInventorieLe(OffsetDateTime.now());
        productRepository.save(produit);

        mouvementRepository.save(MouvementStock.builder()
                .product(produit)
                .delta(0)
                .stockApres(stock)
                .motif(MotifMouvementStock.INVENTAIRE)
                .auteur(auteurId == null ? null : userRepository.findById(auteurId).orElse(null))
                .commentaire(commentaire == null ? "Comptage conforme" : commentaire)
                .build());

        return stock;
    }

    private int lireStock(UUID productId) {
        return productRepository.findById(productId)
                .map(p -> p.getStockQuantity() == null ? 0 : p.getStockQuantity())
                .orElseThrow(() -> new IllegalArgumentException("Produit introuvable : " + productId));
    }

    /**
     * Prévient l'administration au <b>franchissement</b> du seuil, pas tant qu'on est dessous.
     *
     * <p>Alerter à chaque mouvement d'un produit déjà critique produirait une notification par
     * vente, et le signal se noierait en une journée. La condition porte donc sur la
     * traversée : on passe d'au-dessus à en dessous. Remonter au-dessus réarme l'alerte, si
     * bien qu'une rechute se signalera de nouveau.</p>
     *
     * <p>La rupture échappe à cette règle. Un produit qui atteint zéro devient invendable,
     * et cela arrive le plus souvent alors qu'il était déjà sous son seuil — la traversée
     * seule l'aurait donc tu. C'est précisément le défaut qu'on cherche à ne plus subir en
     * silence : dix fauteuils releveurs étaient invendables depuis l'import, et personne ne
     * l'a su avant qu'on aille compter.</p>
     */
    private void signalerSiCritique(Product produit, int avant, int apres) {
        int seuil = produit.getStockThreshold() == null ? 0 : produit.getStockThreshold();

        // Devenir invendable est un evenement en soi, et il se produit souvent alors que le
        // produit etait DEJA sous son seuil : la regle de traversee seule l'aurait tu. Une
        // rupture se signale donc des qu'on atteint zero en venant d'au-dessus, seuil ou pas.
        boolean rupture = apres == 0 && avant > 0;
        boolean franchitLeSeuil = seuil > 0 && avant > seuil && apres <= seuil;
        if (!rupture && !franchitLeSeuil) {
            return;
        }
        try {
            // notifyAdmins et non notifyRole(ADMIN) : le service documente lui-meme le piege,
            // le role ADMIN pouvant n'etre porte par aucun compte sur une plateforme pilotee
            // par un unique SUPER_ADMIN. L'alerte partirait alors dans le vide, sans erreur.
            notifications.notifyAdmins("STOCK_CRITIQUE",
                    rupture ? NotificationSeverity.CRITICAL : NotificationSeverity.WARNING,
                    rupture ? "Rupture de stock" : "Stock critique",
                    rupture
                            ? "« " + produit.getName() + " » est épuisé : il n'est plus commandable."
                            : "« " + produit.getName() + " » descend à " + apres
                              + " unité(s), sous son seuil de " + seuil + ".",
                    "/admin/stock", null,
                    // Une clé par produit : deux références critiques font deux alertes, mais
                    // la même référence ne se répète pas tant que la précédente n'est pas
                    // acquittée.
                    "STOCK_CRITIQUE:" + produit.getId());
        } catch (Exception e) {
            // La veille ne doit jamais faire échouer une vente : le stock est déjà à jour.
            log.warn("Alerte de stock non envoyee pour {} (ignoree) : {}", produit.getId(), e.getMessage());
        }
    }
}
