package com.optimisante.backend.domain.audit.service;

import com.optimisante.backend.config.tenant.TenantContext;
import com.optimisante.backend.domain.audit.dto.AuditLogDto;
import com.optimisante.backend.domain.audit.dto.AuditStatsDto;
import com.optimisante.backend.domain.audit.entity.AuditLog;
import com.optimisante.backend.domain.audit.repository.AuditLogRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Lecture du journal d'audit pour le Dashboard Governance : recherche filtrée paginée, export
 * CSV, agrégats.
 * <p>
 * La recherche filtrée est faite en SQL natif via {@link JdbcTemplate} en n'ajoutant au WHERE
 * que les conditions réellement fournies. C'est volontaire : la forme JPQL «&nbsp;:param IS NULL
 * OR col = :param&nbsp;» fait échouer PostgreSQL (« could not determine data type of parameter »)
 * sur les paramètres nuls non typés — piège déjà rencontré ailleurs dans ce projet.
 */
@Service
@RequiredArgsConstructor
public class AuditQueryService {

    private static final RowMapper<AuditLog> ROW_MAPPER = (rs, i) -> AuditLog.builder()
            .id(rs.getObject("id", UUID.class))
            .tenantId(rs.getObject("tenant_id", UUID.class))
            .actorUserId(rs.getObject("actor_user_id", UUID.class))
            .actorEmail(rs.getString("actor_email"))
            .actorRole(rs.getString("actor_role"))
            .action(rs.getString("action"))
            .entityType(rs.getString("entity_type"))
            .entityId(rs.getString("entity_id"))
            .httpMethod(rs.getString("http_method"))
            .path(rs.getString("path"))
            .statusCode((Integer) rs.getObject("status_code"))
            .ipAddress(rs.getString("ip_address"))
            .userAgent(rs.getString("user_agent"))
            .summary(rs.getString("summary"))
            .metadata(rs.getString("metadata"))
            .createdAt(toOffset(rs.getObject("created_at")))
            .build();

    private final JdbcTemplate jdbc;
    private final AuditLogRepository auditLogRepository;

    @Transactional(readOnly = true)
    public Page<AuditLogDto> search(String action, String entityType, String actorEmail,
                                    OffsetDateTime from, OffsetDateTime to, int page, int size) {
        int p = Math.max(page, 0);
        int s = Math.min(Math.max(size, 1), 200);

        List<Object> args = new ArrayList<>();
        String where = buildWhere(action, entityType, actorEmail, from, to, args);

        Long total = jdbc.queryForObject("SELECT COUNT(*) FROM audit_logs " + where, Long.class, args.toArray());
        long totalElements = total == null ? 0 : total;

        List<Object> pageArgs = new ArrayList<>(args);
        pageArgs.add(s);
        pageArgs.add(p * s);
        List<AuditLog> rows = jdbc.query(
                "SELECT * FROM audit_logs " + where + " ORDER BY created_at DESC LIMIT ? OFFSET ?",
                ROW_MAPPER, pageArgs.toArray());

        List<AuditLogDto> dtos = rows.stream().map(AuditLogDto::from).toList();
        return new PageImpl<>(dtos, PageRequest.of(p, s), totalElements);
    }

    @Transactional(readOnly = true)
    public List<AuditLog> forExport(String action, String entityType, String actorEmail,
                                    OffsetDateTime from, OffsetDateTime to, int limit) {
        int lim = Math.min(Math.max(limit, 1), 50_000);
        List<Object> args = new ArrayList<>();
        String where = buildWhere(action, entityType, actorEmail, from, to, args);
        args.add(lim);
        return jdbc.query(
                "SELECT * FROM audit_logs " + where + " ORDER BY created_at DESC LIMIT ?",
                ROW_MAPPER, args.toArray());
    }

    @Transactional(readOnly = true)
    public AuditStatsDto stats(int windowDays) {
        UUID tenantId = TenantContext.getTenantId();
        int days = Math.min(Math.max(windowDays, 1), 365);

        List<AuditStatsDto.LabelCount> byAction = auditLogRepository.countByActionSince(tenantId, days).stream()
                .map(r -> AuditStatsDto.LabelCount.builder()
                        .label(r.getLabel()).total(r.getTotal() == null ? 0 : r.getTotal()).build())
                .toList();
        List<AuditStatsDto.LabelCount> topActors = auditLogRepository.topActorsSince(tenantId, days).stream()
                .map(r -> AuditStatsDto.LabelCount.builder()
                        .label(r.getLabel()).total(r.getTotal() == null ? 0 : r.getTotal()).build())
                .toList();

        long total = byAction.stream().mapToLong(AuditStatsDto.LabelCount::getTotal).sum();
        long last24h = auditLogRepository.countByCreatedAtAfter(OffsetDateTime.now().minusDays(1));

        return AuditStatsDto.builder()
                .windowDays(days)
                .totalEntries(total)
                .entriesLast24h(last24h)
                .byAction(byAction)
                .topActors(topActors)
                .build();
    }

    /** Construit la clause WHERE (avec le préfixe "WHERE") en n'incluant que les filtres fournis. */
    private String buildWhere(String action, String entityType, String actorEmail,
                              OffsetDateTime from, OffsetDateTime to, List<Object> args) {
        List<String> clauses = new ArrayList<>();

        UUID tenantId = TenantContext.getTenantId();
        if (tenantId != null) {
            clauses.add("tenant_id = ?");
            args.add(tenantId);
        }
        if (isSet(action)) {
            clauses.add("action = ?");
            args.add(action.trim());
        }
        if (isSet(entityType)) {
            clauses.add("entity_type = ?");
            args.add(entityType.trim());
        }
        if (isSet(actorEmail)) {
            clauses.add("LOWER(actor_email) LIKE LOWER(?)");
            args.add("%" + actorEmail.trim() + "%");
        }
        if (from != null) {
            clauses.add("created_at >= ?");
            args.add(OffsetDateTime.class.cast(from));
        }
        if (to != null) {
            clauses.add("created_at <= ?");
            args.add(OffsetDateTime.class.cast(to));
        }
        return clauses.isEmpty() ? "" : "WHERE " + String.join(" AND ", clauses);
    }

    private static boolean isSet(String s) {
        return s != null && !s.isBlank();
    }

    private static OffsetDateTime toOffset(Object ts) {
        if (ts == null) {
            return null;
        }
        if (ts instanceof OffsetDateTime odt) {
            return odt;
        }
        if (ts instanceof java.time.Instant inst) {
            return inst.atOffset(ZoneOffset.UTC);
        }
        if (ts instanceof java.sql.Timestamp t) {
            return t.toInstant().atOffset(ZoneOffset.UTC);
        }
        return OffsetDateTime.parse(ts.toString());
    }
}
