package com.optimisante.backend.domain.catalog.supplier;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Avancement et clôture d'un import, chacun dans sa propre transaction.
 *
 * <p><b>Bean distinct, pour la même raison que {@link TraitementImportCatalogue}.</b> Spring
 * n'applique {@code @Transactional} qu'en passant d'un bean à l'autre : appelées depuis le
 * traitement lui-même, ces deux méthodes s'exécutaient hors transaction et échouaient — l'import
 * écrivait bien les produits, puis se terminait en « échec » sans raison visible.</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SuiviImportCatalogue {

    private final CatalogImportRepository importRepository;

    /**
     * Sérialiseur construit ici : ce projet n'expose pas de bean {@code ObjectMapper} (même
     * constat que {@code AiService}, où l'injection faisait échouer le démarrage).
     */
    private static final ObjectMapper JSON = new ObjectMapper();

    /** Transaction propre : l'avancement doit être lisible pendant que l'import se poursuit. */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void majAvancement(UUID importId, int traitees, int crees, int majs, int images) {
        importRepository.majAvancement(importId, traitees, crees, majs, images);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void terminer(UUID importId, CatalogImport.Statut statut, String motifEchec, List<String> motifs,
                         int traitees, int crees, int majs, int images) {
        importRepository.findById(importId).ifPresent(imprt -> {
            imprt.setStatus(statut);
            imprt.setFailureReason(motifEchec);
            imprt.setProcessedRows(traitees);
            imprt.setCreatedCount(crees);
            imprt.setUpdatedCount(majs);
            imprt.setImageCount(images);
            imprt.setFinishedAt(OffsetDateTime.now());
            if (!motifs.isEmpty()) {
                try {
                    imprt.setReport(JSON.writeValueAsString(Map.of(
                            "motifs", motifs,
                            "total", motifs.size(),
                            "tronque", motifs.size() >= TraitementImportCatalogue.MOTIFS_MAX)));
                } catch (Exception e) {
                    log.warn("Rapport d'import non enregistré : {}", e.getMessage());
                }
            }
            importRepository.save(imprt);
        });
    }
}
