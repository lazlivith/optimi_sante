package com.optimisante.backend.domain.catalog.supplier;

import com.optimisante.backend.common.storage.DossierStockage;
import com.optimisante.backend.domain.catalog.repository.ProductGalleryImageRepository;
import com.optimisante.backend.common.storage.StorageService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.util.*;

/**
 * Écriture effective d'un catalogue confirmé, hors de la requête HTTP.
 *
 * <p><b>Classe à part, et ce n'est pas un détail.</b> Un {@code @Async} appelé depuis la même
 * classe s'exécute dans le fil courant : Spring ne passe par le proxy que d'un bean à l'autre. Le
 * traitement serait alors resté dans la requête, qui aurait expiré au bout de quelques centaines
 * de lignes.</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TraitementImportCatalogue {

    /** Lignes écrites par transaction. Assez pour être efficace, assez peu pour voir l'avancement. */
    private static final int TAILLE_LOT = 50;
    static final int MOTIFS_MAX = 200;

    private final CatalogImportRepository importRepository;
    private final EcrivainCatalogue ecrivain;
    private final TelechargeurImage telechargeur;
    private final StorageService storageService;
    private final ProductGalleryImageRepository galerieRepository;
    private final SuiviImportCatalogue suivi;
    private final SupplierRepository supplierRepository;

    @Async
    public void traiter(UUID importId, UUID tenantId, UUID supplierId) {
        int crees = 0;
        int majs = 0;
        int images = 0;
        int traitees = 0;
        List<String> motifs = new ArrayList<>();
        try {
            CatalogImport imprt = importRepository.findById(importId).orElseThrow();
            boolean remplacer = Boolean.TRUE.equals(imprt.getReplaceImages());
            // Les visuels d'un fournisseur sont ranges sous son code : plusieurs milliers de
            // fichiers verses en vrac dans « catalogue/produits » s'y melent aux photos
            // televersees a la main, et plus rien ne distingue un lot d'un autre.
            //
            // Lu par le depot, et non par imprt.getSupplier() : le fournisseur est une
            // association paresseuse, et ce traitement s'execute hors de toute session —
            // l'atteindre ici interrompait l'import des la premiere ligne.
            String dossier = supplierRepository.findById(supplierId).map(Supplier::getCode).orElse(null);
            byte[] contenu = storageService.download(imprt.getStorageKey());
            List<FichierCatalogue.Ligne> lignes = FichierCatalogue.lire(contenu, imprt.getFileName()).lignes();

            // Décidé à nouveau, et non repris de l'analyse : le catalogue a pu changer entre les deux.
            List<EcrivainCatalogue.Decision> decisions = ecrivain.decider(tenantId, supplierId, lignes);

            for (int debut = 0; debut < decisions.size(); debut += TAILLE_LOT) {
                List<EcrivainCatalogue.Decision> lot = decisions.subList(debut,
                        Math.min(debut + TAILLE_LOT, decisions.size()));

                // Réseau d'abord, base ensuite : aucune connexion n'est retenue pendant un téléchargement.
                Map<String, EcrivainCatalogue.VisuelsProduit> visuels = visuels(lot, remplacer, dossier);
                EcrivainCatalogue.Resultat resultat = ecrivain.ecrire(tenantId, supplierId, lot, visuels);

                crees += resultat.crees();
                majs += resultat.misAJour();
                images += resultat.images();
                traitees += lot.size();
                resultat.motifs().stream().limit(Math.max(0, MOTIFS_MAX - motifs.size())).forEach(motifs::add);
                suivi.majAvancement(importId, traitees, crees, majs, images);
            }
            suivi.terminer(importId, CatalogImport.Statut.TERMINE, null, motifs, traitees, crees, majs, images);
            log.info("Import {} terminé : {} création(s), {} mise(s) à jour, {} visuel(s)",
                    importId, crees, majs, images);
        } catch (Exception e) {
            log.error("Import {} interrompu : {}", importId, e.getMessage(), e);
            suivi.terminer(importId, CatalogImport.Statut.ECHEC, e.getMessage(), motifs, traitees, crees, majs, images);
        }
    }

    /**
     * Visuels d'un lot, deposes sur l'espace de stockage.
     *
     * <p><b>Par defaut, rien n'est ecrase.</b> Un produit qui porte deja une vignette la garde —
     * un fournisseur renvoie son catalogue chaque mois, et l'administration a pu choisir une
     * meilleure photo entre-temps. Une galerie deja garnie est laissee telle quelle pour la
     * meme raison. Seul ce qui manque est rempli.</p>
     *
     * <p><b>Sauf demande expresse.</b> {@code remplacer} vient d'une case cochee a la main sur
     * l'ecran d'import : la vignette est alors reprise du fournisseur, et la galerie refaite.
     * C'est ce qu'il faut pour un premier chargement de gamme, ou quand le fournisseur a
     * rephotographie son catalogue — jamais le comportement par defaut.</p>
     *
     * <p><b>Pourquoi toutes les images, et plus seulement la premiere.</b> Le fournisseur en
     * publie plusieurs par reference — angles, zooms, schemas de cotes. N'en retenir qu'une
     * laissait la fiche detaillee aussi pauvre que la vignette, sur du materiel a plusieurs
     * centaines d'euros ou le client cherche precisement a voir avant d'acheter.</p>
     */
    private Map<String, EcrivainCatalogue.VisuelsProduit> visuels(
            List<EcrivainCatalogue.Decision> lot, boolean remplacer, String sousDossier) {
        List<UUID> produits = lot.stream()
                .map(EcrivainCatalogue.Decision::produitId)
                .filter(Objects::nonNull).toList();
        Set<UUID> avecGalerie = produits.isEmpty() ? Set.of()
                : new HashSet<>(galerieRepository.produitsAvecGalerie(produits));

        Map<String, EcrivainCatalogue.VisuelsProduit> visuels = new HashMap<>();
        for (EcrivainCatalogue.Decision decision : lot) {
            if (decision.action() == EcrivainCatalogue.Action.IGNORER
                    || decision.ligne().images().isEmpty()) {
                continue;
            }
            boolean poserPrincipal = remplacer || !decision.aDejaImage();
            boolean poserGalerie = remplacer
                    || decision.produitId() == null || !avecGalerie.contains(decision.produitId());
            if (!poserPrincipal && !poserGalerie) {
                continue;
            }

            List<String> adresses = poserGalerie
                    ? decision.ligne().images()
                    : decision.ligne().images().subList(0, 1);

            EcrivainCatalogue.Visuel principal = null;
            List<EcrivainCatalogue.Visuel> galerie = new ArrayList<>();
            for (String adresse : adresses) {
                // La vignette et les vues secondaires ne vivent pas au meme endroit : la premiere
                // s'affiche dans toutes les listes, les autres seulement sur la fiche. Les melanger
                // rendait impossible de distinguer, dans l'espace de stockage, ce qui est servi a
                // chaque page de ce qui ne l'est qu'a l'ouverture d'un produit.
                boolean enVignette = poserPrincipal && principal == null;
                EcrivainCatalogue.Visuel depose = deposer(decision.ligne().sku(), adresse, sousDossier,
                        enVignette ? DossierStockage.CATALOGUE_PRODUITS : DossierStockage.CATALOGUE_GALERIE);
                if (depose == null) {
                    continue;
                }
                if (enVignette) {
                    principal = depose;
                } else if (poserGalerie) {
                    galerie.add(depose);
                }
            }
            if (principal != null || !galerie.isEmpty()) {
                if (remplacer && decision.produitId() != null && !galerie.isEmpty()) {
                    purgerGalerie(decision.produitId());
                }
                visuels.put(decision.ligne().sku(), new EcrivainCatalogue.VisuelsProduit(principal, galerie));
            }
        }
        return visuels;
    }

    /** @return le visuel depose, ou nul : un produit vaut mieux sans photo que pas de produit. */
    private EcrivainCatalogue.Visuel deposer(String sku, String adresse, String sousDossier,
                                             DossierStockage dossier) {
        return telechargeur.telecharger(adresse).map(image -> {
            try {
                String nom = nomLisible(sku, image.nomFichier());
                String cle = storageService.uploadMedia(image.contenu(), nom, dossier, sousDossier);
                return new EcrivainCatalogue.Visuel(
                        storageService.generateMediaUrl(cle, "image", extension(nom)), cle);
            } catch (Exception e) {
                log.warn("Visuel non enregistre pour {} : {}", sku, e.getMessage());
                return null;
            }
        }).orElse(null);
    }

    /**
     * Retire les visuels secondaires d'un produit, fichiers compris.
     *
     * <p><b>Pourquoi ici et pas a l'ecriture.</b> Supprimer un fichier chez l'hebergeur est un
     * appel reseau : le faire dans la transaction d'ecriture la tiendrait ouverte pendant
     * plusieurs secondes, cinquante fois par lot. La purge suit donc les telechargements, du
     * meme cote de la frontiere.</p>
     *
     * <p><b>Et seulement une fois les nouvelles images obtenues.</b> Purger avant exposerait a
     * perdre la galerie si le fournisseur ne repond pas : le produit se retrouverait sans
     * visuel du tout, moins bien qu'avant l'import.</p>
     */
    private void purgerGalerie(UUID produitId) {
        var anciens = galerieRepository.findByProductIdOrderByDisplayOrderAscCreatedAtAsc(produitId);
        for (var ancien : anciens) {
            if (ancien.getPublicId() != null && !ancien.getPublicId().isBlank()) {
                try {
                    storageService.deleteFile(ancien.getPublicId());
                } catch (Exception e) {
                    // Un fichier qui resiste est facture, pas bloquant : la ligne part quand meme,
                    // sans quoi l'import s'arreterait sur un menage.
                    log.warn("Ancien visuel {} non supprime : {}", ancien.getPublicId(), e.getMessage());
                }
            }
        }
        galerieRepository.deleteAll(anciens);
    }

    /**
     * Nomme le fichier d'apres la reference du produit.
     *
     * <p>Le fournisseur sert ses images sous une empreinte — {@code 654edfde5fd9.jpg} — qui ne
     * dit rien une fois le fichier depose. La reference, elle, permet de retrouver tous les
     * visuels d'un produit depuis la console de stockage, sans passer par la base.</p>
     */
    private static String nomLisible(String sku, String nomDOrigine) {
        return sku + (nomDOrigine == null || !nomDOrigine.contains(".") ? ""
                : nomDOrigine.substring(nomDOrigine.lastIndexOf('.')));
    }

    /** Extension sans point, vide si le nom n'en porte pas. */
    private static String extension(String nomFichier) {
        int point = nomFichier == null ? -1 : nomFichier.lastIndexOf('.');
        return point < 0 ? "" : nomFichier.substring(point + 1);
    }
}
