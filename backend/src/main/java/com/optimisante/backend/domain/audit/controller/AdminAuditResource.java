package com.optimisante.backend.domain.audit.controller;

import com.optimisante.backend.config.security.PlatformAdmin;

import com.optimisante.backend.common.export.CsvWriter;
import com.optimisante.backend.domain.audit.dto.AuditLogDto;
import com.optimisante.backend.domain.audit.dto.AuditStatsDto;
import com.optimisante.backend.domain.audit.entity.AuditLog;
import com.optimisante.backend.domain.audit.service.AuditQueryService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;
import java.util.List;

/**
 * Journal d'audit du Dashboard Governance : consultation filtrée, agrégats, export CSV.
 * Lecture accessible à toute l'administration (ADMIN + SUPER_ADMIN) — la partie sensible
 * (anonymisation RGPD) est, elle, réservée au SUPER_ADMIN dans AdminGovernanceResource.
 */
/**
 * Perimetre d'acces : supervision (SUPER_ADMIN, ADMIN herite).
 *
 * <p>Journal des acces de toute la plateforme : releve de la supervision, pas d'un metier.</p>
 */
@RestController
@RequestMapping("/api/v1/admin/audit")
@RequiredArgsConstructor
@PlatformAdmin
public class AdminAuditResource {

    private final AuditQueryService auditQueryService;

    @GetMapping("/logs")
    public ResponseEntity<Page<AuditLogDto>> list(
            @RequestParam(required = false) String action,
            @RequestParam(required = false) String entityType,
            @RequestParam(required = false) String actorEmail,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime to,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "25") int size) {
        return ResponseEntity.ok(auditQueryService.search(action, entityType, actorEmail, from, to, page, size));
    }

    @GetMapping("/stats")
    public ResponseEntity<AuditStatsDto> stats(@RequestParam(defaultValue = "30") int windowDays) {
        return ResponseEntity.ok(auditQueryService.stats(windowDays));
    }

    @GetMapping("/logs/export")
    public ResponseEntity<byte[]> export(
            @RequestParam(required = false) String action,
            @RequestParam(required = false) String entityType,
            @RequestParam(required = false) String actorEmail,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime to,
            @RequestParam(defaultValue = "10000") int limit) {

        List<AuditLog> rows = auditQueryService.forExport(action, entityType, actorEmail, from, to, limit);
        List<String> headers = List.of("Horodatage", "Acteur", "Rôle", "Action", "Type entité",
                "ID entité", "Méthode", "Chemin", "Statut HTTP", "IP", "Résumé");
        List<List<Object>> data = rows.stream().map(a -> List.<Object>of(
                nz(a.getCreatedAt()), nz(a.getActorEmail()), nz(a.getActorRole()), nz(a.getAction()),
                nz(a.getEntityType()), nz(a.getEntityId()), nz(a.getHttpMethod()), nz(a.getPath()),
                nz(a.getStatusCode()), nz(a.getIpAddress()), nz(a.getSummary())
        )).toList();

        byte[] body = CsvWriter.toCsv(headers, data).getBytes(StandardCharsets.UTF_8);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"audit-log.csv\"")
                .contentType(MediaType.parseMediaType("text/csv; charset=UTF-8"))
                .body(body);
    }

    private static Object nz(Object v) {
        return v == null ? "" : v;
    }
}
