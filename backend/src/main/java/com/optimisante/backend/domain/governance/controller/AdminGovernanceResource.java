package com.optimisante.backend.domain.governance.controller;

import com.optimisante.backend.config.security.PlatformAdmin;

import com.optimisante.backend.domain.governance.dto.RetentionReportDto;
import com.optimisante.backend.domain.governance.dto.SoftDeleteReportDto;
import com.optimisante.backend.domain.governance.entity.RgpdRequest;
import com.optimisante.backend.domain.governance.repository.RgpdRequestRepository;
import com.optimisante.backend.domain.governance.service.GovernanceService;
import com.optimisante.backend.domain.governance.service.RgpdService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * Dashboard Governance — volet conformité / RGPD. C'est ici que se matérialise la séparation
 * <b>Dual-Admin</b> demandée par le CDC :
 * <ul>
 *   <li><b>ADMIN</b> (administration opérationnelle) : consultation des rapports de conformité,
 *       recherche et export des données d'une personne concernée.</li>
 *   <li><b>SUPER_ADMIN</b> (gouvernance) : seul habilité à déclencher l'anonymisation
 *       irréversible d'une personne (droit à l'effacement).</li>
 * </ul>
 */
/**
 * Perimetre d'acces : supervision (SUPER_ADMIN, ADMIN herite).
 *
 * <p>RGPD, retention, anonymisation : decisions de plateforme, jamais de perimetre.</p>
 */
@RestController
@RequestMapping("/api/v1/admin/governance")
@RequiredArgsConstructor
public class AdminGovernanceResource {

    private final GovernanceService governanceService;
    private final RgpdService rgpdService;
    private final RgpdRequestRepository rgpdRequestRepository;

    // ---------------------------------------------------------------- rapports conformité (ADMIN+)

    @GetMapping("/soft-delete-report")
    @PlatformAdmin
    public ResponseEntity<SoftDeleteReportDto> softDeleteReport() {
        return ResponseEntity.ok(governanceService.softDeleteReport());
    }

    @GetMapping("/retention-report")
    @PlatformAdmin
    public ResponseEntity<RetentionReportDto> retentionReport(@RequestParam(defaultValue = "24") int months) {
        return ResponseEntity.ok(governanceService.retentionReport(months));
    }

    @GetMapping("/rgpd/requests")
    @PlatformAdmin
    public ResponseEntity<Page<RgpdRequest>> rgpdRequests(@RequestParam(defaultValue = "0") int page,
                                                          @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(rgpdRequestRepository.findAllByOrderByCreatedAtDesc(
                PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), 100))));
    }

    // ---------------------------------------------------------------- droit d'accès (ADMIN+)

    /** Aperçu léger de l'empreinte de données d'une personne, avant décision. */
    @GetMapping("/rgpd/subject")
    @PlatformAdmin
    public ResponseEntity<Map<String, Object>> lookup(@RequestParam String email) {
        return ResponseEntity.ok(rgpdService.lookup(email));
    }

    /**
     * Export complet des données personnelles (droit d'accès / portabilité). Le corps JSON est
     * renvoyé tel quel ; le frontend le propose au téléchargement en fichier .json.
     */
    @GetMapping("/rgpd/subject/export")
    @PlatformAdmin
    public ResponseEntity<Map<String, Object>> export(@RequestParam String email) {
        return ResponseEntity.ok(rgpdService.export(email));
    }

    // ---------------------------------------------------------------- droit à l'effacement (SUPER_ADMIN)

    @PostMapping("/rgpd/subject/anonymize")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public ResponseEntity<Map<String, Object>> anonymize(@RequestBody AnonymizeRequest body) {
        return ResponseEntity.ok(rgpdService.anonymize(body.email()));
    }

    public record AnonymizeRequest(String email) {
    }
}
