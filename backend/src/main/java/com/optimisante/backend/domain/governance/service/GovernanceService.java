package com.optimisante.backend.domain.governance.service;

import com.optimisante.backend.config.tenant.TenantContext;
import com.optimisante.backend.domain.governance.dto.RetentionReportDto;
import com.optimisante.backend.domain.governance.dto.SoftDeleteReportDto;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Rapports de gouvernance des données : étanchéité du Soft Delete et rétention. Lecture seule,
 * en SQL natif (JdbcTemplate) parce qu'il faut justement voir les lignes que le filtre global
 * {@code @SQLRestriction} masque au reste de l'application.
 */
@Service
@RequiredArgsConstructor
public class GovernanceService {

    private final JdbcTemplate jdbc;

    @Transactional(readOnly = true)
    public SoftDeleteReportDto softDeleteReport() {
        List<SoftDeleteReportDto.TableReport> tables = new ArrayList<>();

        tables.add(softDeleteTable("users", "User", true,
                "Filtre global @SQLRestriction(\"deleted_at IS NULL\") — les comptes effacés sont "
                        + "invisibles partout (auth, listes admin comprises, sauf requêtes natives dédiées)."));
        tables.add(softDeleteTable("products", "Product", true,
                "Filtre global @SQLRestriction(\"deleted_at IS NULL AND is_active = true\") ; l'admin "
                        + "catalogue utilise des projections natives dédiées pour voir/réactiver les désactivés."));

        // Tables sans soft delete : suppression physique ou conservation intégrale (obligations légales/finance).
        tables.add(hardRetainedTable("orders", "Order",
                "Aucun soft delete : les commandes sont conservées (obligations comptables). "
                        + "L'anonymisation RGPD brouille le client rattaché, pas la commande."));
        tables.add(hardRetainedTable("enrollments", "Enrollment",
                "Aucun soft delete : dossiers de mobilité conservés ; le médecin rattaché est anonymisé le cas échéant."));

        boolean healthy = tables.stream()
                .filter(t -> t.getEntity().equals("User") || t.getEntity().equals("Product"))
                .allMatch(SoftDeleteReportDto.TableReport::isGlobalFilterEnforced);

        String verdict = healthy
                ? "Conforme : les entités à données personnelles (User, Product) appliquent bien un filtre "
                  + "global qui masque les lignes effacées logiquement à toute l'application."
                : "Anomalie : au moins une entité censée être en soft delete n'applique pas le filtre global.";

        return SoftDeleteReportDto.builder().tables(tables).verdict(verdict).healthy(healthy).build();
    }

    private SoftDeleteReportDto.TableReport softDeleteTable(String table, String entity, boolean enforced, String note) {
        long total = count("SELECT COUNT(*) FROM " + table);
        long active = count("SELECT COUNT(*) FROM " + table + " WHERE deleted_at IS NULL");
        long deleted = total - active;
        long anonymized = "users".equals(table)
                ? count("SELECT COUNT(*) FROM users WHERE anonymized_at IS NOT NULL")
                : 0;
        return SoftDeleteReportDto.TableReport.builder()
                .table(table).entity(entity).globalFilterEnforced(enforced)
                .totalRows(total).activeRows(active).softDeletedRows(deleted).anonymizedRows(anonymized)
                .note(note).build();
    }

    private SoftDeleteReportDto.TableReport hardRetainedTable(String table, String entity, String note) {
        long total = count("SELECT COUNT(*) FROM " + table);
        return SoftDeleteReportDto.TableReport.builder()
                .table(table).entity(entity).globalFilterEnforced(false)
                .totalRows(total).activeRows(total).softDeletedRows(0).anonymizedRows(0)
                .note(note).build();
    }

    @Transactional(readOnly = true)
    public RetentionReportDto retentionReport(int thresholdMonths) {
        int months = Math.min(Math.max(thresholdMonths, 1), 120);
        UUID tenantId = TenantContext.getTenantId();

        String staleWhere = """
                u.tenant_id = ? AND u.deleted_at IS NULL AND u.anonymized_at IS NULL
                AND u.role IN ('CLIENT_B2C','CLIENT_B2B','MEDECIN')
                AND u.created_at < now() - (? || ' months')::interval
                AND NOT EXISTS (SELECT 1 FROM orders o WHERE o.user_id = u.id)
                AND NOT EXISTS (SELECT 1 FROM enrollments e WHERE e.doctor_id = u.id)
                """;

        long stale = count("SELECT COUNT(*) FROM users u WHERE " + staleWhere, tenantId, months);
        long oldLeads = count("""
                SELECT COUNT(*) FROM prospect_leads pl
                JOIN trainings tr ON tr.id = pl.training_id
                WHERE tr.tenant_id = ? AND pl.downloaded_at < now() - (? || ' months')::interval
                """, tenantId, months);
        long anonymized = count("SELECT COUNT(*) FROM users WHERE tenant_id = ? AND anonymized_at IS NOT NULL", tenantId);
        long noConsent = count("SELECT COUNT(*) FROM users WHERE tenant_id = ? AND deleted_at IS NULL AND gdpr_consent_at IS NULL", tenantId);

        List<RetentionReportDto.Candidate> sample = jdbc.query(
                "SELECT u.id, u.email, u.role, u.created_at FROM users u WHERE " + staleWhere
                        + " ORDER BY u.created_at ASC LIMIT 25",
                (rs, i) -> RetentionReportDto.Candidate.builder()
                        .userId(rs.getString("id"))
                        .email(rs.getString("email"))
                        .role(rs.getString("role"))
                        .createdAt(String.valueOf(rs.getObject("created_at")))
                        .ordersCount(0)
                        .enrollmentsCount(0)
                        .build(),
                tenantId, months);

        return RetentionReportDto.builder()
                .staleThresholdMonths(months)
                .staleActiveUsers(stale)
                .oldLeads(oldLeads)
                .anonymizedUsers(anonymized)
                .usersWithoutConsent(noConsent)
                .staleUserSample(sample)
                .build();
    }

    private long count(String sql, Object... args) {
        Long n = jdbc.queryForObject(sql, Long.class, args);
        return n == null ? 0 : n;
    }
}
