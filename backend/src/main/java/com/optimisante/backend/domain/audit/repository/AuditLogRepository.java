package com.optimisante.backend.domain.audit.repository;

import com.optimisante.backend.domain.audit.entity.AuditLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

/**
 * Le journal d'audit ne fait que des écritures (via {@code AuditLogWriter}) et de la lecture
 * agrégée. La recherche filtrée paginée est en SQL natif dynamique dans {@code AuditQueryService}
 * (le pattern JPQL «&nbsp;:param IS NULL OR ...&nbsp;» fait échouer PostgreSQL sur les paramètres
 * nuls non typés).
 */
@Repository
public interface AuditLogRepository extends JpaRepository<AuditLog, UUID> {

    @Query(value = """
            SELECT a.action AS label, COUNT(*) AS total
            FROM audit_logs a
            WHERE (:tenantId IS NULL OR a.tenant_id = :tenantId)
              AND a.created_at >= now() - (:days || ' days')::interval
            GROUP BY a.action
            ORDER BY total DESC
            """, nativeQuery = true)
    List<LabelCountRow> countByActionSince(@Param("tenantId") UUID tenantId, @Param("days") int days);

    @Query(value = """
            SELECT COALESCE(a.actor_email, '(système)') AS label, COUNT(*) AS total
            FROM audit_logs a
            WHERE (:tenantId IS NULL OR a.tenant_id = :tenantId)
              AND a.created_at >= now() - (:days || ' days')::interval
            GROUP BY a.actor_email
            ORDER BY total DESC
            LIMIT 10
            """, nativeQuery = true)
    List<LabelCountRow> topActorsSince(@Param("tenantId") UUID tenantId, @Param("days") int days);

    long countByCreatedAtAfter(OffsetDateTime since);

    /** Projection générique libellé + total, réutilisée par les agrégats d'audit. */
    interface LabelCountRow {
        String getLabel();
        Long getTotal();
    }
}
