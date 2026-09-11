package com.optimisante.backend.domain.reporting;

import com.optimisante.backend.config.security.PlatformAdmin;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * Exposition côté admin du worker Python de reporting : catalogue de rapports, déclenchement
 * d'une exécution, historique des exécutions, téléchargement du dernier livrable (CSV / XLSX).
 * Toutes les requêtes transitent par le backend pour que le RBAC JWT soit appliqué (le worker
 * n'est pas exposé publiquement).
 */
/**
 * Perimetre d'acces : supervision (SUPER_ADMIN, ADMIN herite).
 *
 * <p>Les sept rapports couvrent les deux metiers et la conformite : transverse par construction.</p>
 */
@RestController
@RequestMapping("/api/v1/admin/reporting")
@RequiredArgsConstructor
@PlatformAdmin
public class AdminReportingResource {

    private final ReportingClient reportingClient;

    @GetMapping("/status")
    public ResponseEntity<Map<String, Object>> status() {
        if (!reportingClient.isConfigured()) {
            return ResponseEntity.ok(Map.<String, Object>of("configured", false, "reachable", false));
        }
        try {
            Map<String, Object> health = reportingClient.health();
            return ResponseEntity.ok(Map.<String, Object>of("configured", true, "reachable", true, "worker", health));
        } catch (Exception e) {
            return ResponseEntity.ok(Map.<String, Object>of(
                    "configured", true, "reachable", false,
                    "error", e.getMessage() == null ? "unreachable" : e.getMessage()));
        }
    }

    @GetMapping("/reports")
    public ResponseEntity<List<Map<String, Object>>> reports() {
        return ResponseEntity.ok(reportingClient.listReports());
    }

    @GetMapping("/reports/{key}/runs")
    public ResponseEntity<List<Map<String, Object>>> runs(@PathVariable String key,
                                                          @RequestParam(defaultValue = "20") int limit) {
        return ResponseEntity.ok(reportingClient.runs(key, limit));
    }

    @GetMapping("/reports/{key}/preview")
    public ResponseEntity<Map<String, Object>> preview(@PathVariable String key,
                                                       @RequestParam(defaultValue = "50") int limit) {
        return ResponseEntity.ok(reportingClient.preview(key, limit));
    }

    @PostMapping("/reports/{key}/run")
    public ResponseEntity<Map<String, Object>> run(@PathVariable String key,
                                                   @RequestBody(required = false) Map<String, Object> params) {
        return ResponseEntity.ok(reportingClient.run(key, "MANUAL", params));
    }

    @GetMapping("/reports/{key}/download/latest")
    public ResponseEntity<byte[]> download(@PathVariable String key,
                                           @RequestParam(defaultValue = "csv") String format) {
        byte[] body = reportingClient.downloadLatest(key, format);
        String ext = "xlsx".equalsIgnoreCase(format) ? "xlsx" : "csv";
        MediaType type = "xlsx".equalsIgnoreCase(format)
                ? MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")
                : MediaType.parseMediaType("text/csv; charset=UTF-8");
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + key + "." + ext + "\"")
                .contentType(type)
                .body(body);
    }
}
