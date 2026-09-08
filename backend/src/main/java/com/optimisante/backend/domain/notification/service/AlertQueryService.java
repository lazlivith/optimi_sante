package com.optimisante.backend.domain.notification.service;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Lecture filtrée des alertes pour l'écran admin « Alertes ». SQL natif via {@link JdbcTemplate}
 * en n'ajoutant au WHERE que les filtres fournis — même parti pris que {@code AuditQueryService}
 * (le pattern JPQL «&nbsp;:param IS NULL OR ...&nbsp;» échoue sur PostgreSQL avec des paramètres
 * nuls non typés). Toujours borné au destinataire (l'admin courant).
 */
@Service
@RequiredArgsConstructor
public class AlertQueryService {

    private final JdbcTemplate jdbc;

    @Transactional(readOnly = true)
    public Page<Map<String, Object>> search(UUID recipientUserId, List<String> severities,
                                            String status, int page, int size) {
        int p = Math.max(page, 0);
        int s = Math.min(Math.max(size, 1), 100);

        List<Object> args = new ArrayList<>();
        StringBuilder where = new StringBuilder("WHERE recipient_user_id = ?");
        args.add(recipientUserId);

        if (severities != null && !severities.isEmpty()) {
            where.append(" AND severity IN (")
                    .append("?,".repeat(severities.size() - 1)).append("?)");
            args.addAll(severities);
        }
        if ("open".equalsIgnoreCase(status)) {
            where.append(" AND acknowledged_at IS NULL");
        } else if ("acknowledged".equalsIgnoreCase(status)) {
            where.append(" AND acknowledged_at IS NOT NULL");
        }

        Long total = jdbc.queryForObject("SELECT COUNT(*) FROM notifications " + where, Long.class, args.toArray());
        long totalElements = total == null ? 0 : total;

        List<Object> pageArgs = new ArrayList<>(args);
        pageArgs.add(s);
        pageArgs.add(p * s);
        List<Map<String, Object>> rows = jdbc.query(
                "SELECT id, type, severity, title, body, link_url, group_count, "
                        + "read_at, acknowledged_at, snoozed_until, created_at "
                        + "FROM notifications " + where + " ORDER BY created_at DESC LIMIT ? OFFSET ?",
                (rs, i) -> {
                    Map<String, Object> m = new LinkedHashMap<>();
                    m.put("id", rs.getString("id"));
                    m.put("type", rs.getString("type"));
                    m.put("severity", rs.getString("severity"));
                    m.put("title", rs.getString("title"));
                    m.put("body", rs.getString("body"));
                    m.put("linkUrl", rs.getString("link_url"));
                    m.put("groupCount", rs.getInt("group_count"));
                    m.put("read", rs.getObject("read_at") != null);
                    m.put("acknowledged", rs.getObject("acknowledged_at") != null);
                    m.put("snoozedUntil", toOffset(rs.getObject("snoozed_until")));
                    m.put("createdAt", toOffset(rs.getObject("created_at")));
                    return m;
                },
                pageArgs.toArray());

        return new PageImpl<>(rows, PageRequest.of(p, s), totalElements);
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
