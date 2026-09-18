package com.optimisante.backend.domain.catalog.supplier;

import com.optimisante.backend.common.storage.DossierStockage;
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
    private final SuiviImportCatalogue suivi;

    @Async
    public void traiter(UUID importId, UUID tenantId, UUID supplierId) {
        int crees = 0;
        int majs = 0;
        int images = 0;
        int traitees = 0;
        List<String> motifs = new ArrayList<>();
        try {
            CatalogImport imprt = importRepository.findById(importId).orElseThrow();
            byte[] contenu = storageService.download(imprt.getStorageKey());
            List<FichierCatalogue.Ligne> lignes = FichierCatalogue.lire(contenu, imprt.getFileName()).lignes();

            // Décidé à nouveau, et non repris de l'analyse : le catalogue a pu changer entre les deux.
            List<EcrivainCatalogue.Decision> decisions = ecrivain.decider(tenantId, supplierId, lignes);

            for (int debut = 0; debut < decisions.size(); debut += TAILLE_LOT) {
                List<EcrivainCatalogue.Decision> lot = decisions.subList(debut,
                        Math.min(debut + TAILLE_LOT, decisions.size()));

                // Réseau d'abord, base ensuite : aucune connexion n'est retenue pendant un téléchargement.
                Map<String, String> visuels = visuels(lot);
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
     * Visuels d'un lot. Une image n'est récupérée que si le produit n'en a pas : un fournisseur qui
     * renvoie son catalogue chaque mois ne doit pas re-téléverser deux mille fois les mêmes photos.
     */
    private Map<String, String> visuels(List<EcrivainCatalogue.Decision> lot) {
        Map<String, String> visuels = new HashMap<>();
        for (EcrivainCatalogue.Decision decision : lot) {
            if (decision.action() == EcrivainCatalogue.Action.IGNORER
                    || decision.aDejaImage() || decision.ligne().images().isEmpty()) {
                continue;
            }
            telechargeur.telecharger(decision.ligne().images().get(0)).ifPresent(image -> {
                try {
                    String cle = storageService.uploadMedia(image.contenu(), image.nomFichier(),
                            DossierStockage.CATALOGUE_PRODUITS);
                    visuels.put(decision.ligne().sku(), storageService.generateMediaUrl(cle, "image"));
                } catch (Exception e) {
                    // Le produit vaut mieux sans visuel que pas de produit du tout.
                    log.warn("Visuel non enregistré pour {} : {}", decision.ligne().sku(), e.getMessage());
                }
            });
        }
        return visuels;
    }
}
