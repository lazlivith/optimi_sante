package com.optimisante.backend.domain.catalog.service;

import com.optimisante.backend.domain.catalog.entity.Product;
import com.optimisante.backend.domain.catalog.repository.ProductRepository;
import com.optimisante.backend.domain.identity.repository.CompanyProfileRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * La taxe contenue dans un panier, avant que la commande n'existe.
 *
 * <p><b>Pourquoi le serveur, et pas le navigateur.</b> La ventilation demande le taux de chaque
 * article — il dépend de son rayon, parfois de l'article lui-même — et le prix réellement
 * applicable, qui tient compte d'une promotion en cours et de la remise du compte
 * professionnel. Laisser le navigateur calculer cela supposerait de lui livrer toute la grille
 * des taux et toute la règle de prix, puis de faire confiance au résultat. Les prix affichés
 * seraient alors ceux que le client veut bien, et non ceux que la plateforme facture.</p>
 *
 * <p><b>La remise est répartie au prorata.</b> Un code promo de 50 € sur un panier mêlant du
 * 5,5 % et du 20 % ne réduit pas une seule des deux bases : il les réduit toutes deux, chacune
 * à hauteur de son poids. Sans cela, le total hors taxes et la taxe annoncés dépasseraient le
 * montant réellement réglé — un écart qui se voit immédiatement sur un reçu.</p>
 *
 * <p><b>Les frais de port restent dehors.</b> Leur régime suit en principe celui de la
 * marchandise, mais rien ne tranche le cas d'un panier mixte : c'est l'une des questions
 * encore ouvertes chez le comptable. Les ventiler au jugé ferait annoncer un taux que personne
 * n'a établi.</p>
 */
@Service
@RequiredArgsConstructor
public class ServiceVentilationPanier {

    private final ProductRepository productRepository;
    private final CompanyProfileRepository companyProfileRepository;
    private final ServiceTva serviceTva;

    /** Une ligne du panier, telle que le navigateur la décrit. */
    public record LignePanier(UUID productId, int quantity) {}

    /**
     * La taxe contenue dans le panier.
     *
     * @param affichee  faux tant que la grille des taux n'est pas ouverte : l'écran n'affiche
     *                  alors rien, plutôt qu'un détail que les documents ne reprendraient pas
     * @param ventilation une entrée par taux présent dans le panier
     */
    public record Ventilation(boolean affichee, BigDecimal totalHt, BigDecimal totalTva,
                              List<Taux> ventilation) {}

    public record Taux(BigDecimal taux, BigDecimal ht, BigDecimal taxe) {}

    @Transactional(readOnly = true)
    public Ventilation ventiler(List<LignePanier> lignes, BigDecimal remise) {
        if (!serviceTva.estPubliable()) {
            // Fermé : on ne rend aucun chiffre, plutôt qu'un chiffre que l'écran devrait
            // ensuite décider de cacher. La règle d'affichage tient à un seul endroit.
            return new Ventilation(false, BigDecimal.ZERO, BigDecimal.ZERO, List.of());
        }
        if (lignes == null || lignes.isEmpty()) {
            return new Ventilation(true, BigDecimal.ZERO, BigDecimal.ZERO, List.of());
        }

        BigDecimal tauxRemiseB2B = remiseDuCompte();

        List<ServiceTva.LigneTaxable> taxables = new ArrayList<>();
        BigDecimal totalArticles = BigDecimal.ZERO;

        for (LignePanier ligne : lignes) {
            if (ligne.quantity() <= 0) continue;
            Product produit = productRepository.findById(ligne.productId()).orElse(null);
            if (produit == null || Boolean.TRUE.equals(produit.getIsQuoteOnly())) continue;

            BigDecimal prixUnitaire = produit.getEffectiveBasePrice();
            if (tauxRemiseB2B.signum() > 0) {
                prixUnitaire = prixUnitaire
                        .multiply(BigDecimal.ONE.subtract(
                                tauxRemiseB2B.divide(BigDecimal.valueOf(100), 6, RoundingMode.HALF_UP)))
                        .setScale(2, RoundingMode.HALF_UP);
            }
            BigDecimal sousTotal = prixUnitaire.multiply(BigDecimal.valueOf(ligne.quantity()));
            totalArticles = totalArticles.add(sousTotal);
            taxables.add(new ServiceTva.LigneTaxable(sousTotal, serviceTva.tauxDe(produit)));
        }

        if (taxables.isEmpty()) {
            return new Ventilation(true, BigDecimal.ZERO, BigDecimal.ZERO, List.of());
        }

        List<ServiceTva.LigneTaxable> apresRemise = serviceTva.repartirRemise(taxables, remise);
        List<ServiceTva.Ventilation> ventilation = serviceTva.ventiler(apresRemise);

        BigDecimal totalHt = ventilation.stream().map(ServiceTva.Ventilation::ht)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal totalTva = ventilation.stream().map(ServiceTva.Ventilation::taxe)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        return new Ventilation(true, totalHt, totalTva,
                ventilation.stream().map(v -> new Taux(v.taux(), v.ht(), v.taxe())).toList());
    }

    /** La remise du compte professionnel, nulle pour un particulier ou un visiteur. */
    private BigDecimal remiseDuCompte() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated()
                || "anonymousUser".equals(auth.getPrincipal())) {
            return BigDecimal.ZERO;
        }
        boolean estB2B = auth.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().contains("CLIENT_B2B")
                        || a.getAuthority().contains("CENTRE_FORMATION"));
        if (!estB2B) {
            return BigDecimal.ZERO;
        }
        try {
            UUID userId = UUID.fromString(auth.getPrincipal().toString());
            return companyProfileRepository.findByUserId(userId)
                    .map(p -> p.getB2bDiscountRate() == null ? BigDecimal.ZERO : p.getB2bDiscountRate())
                    .orElse(BigDecimal.ZERO);
        } catch (IllegalArgumentException e) {
            return BigDecimal.ZERO;
        }
    }
}
