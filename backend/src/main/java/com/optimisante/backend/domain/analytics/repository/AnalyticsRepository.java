package com.optimisante.backend.domain.analytics.repository;

import com.optimisante.backend.domain.orders.entity.Order;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Agrégats analytiques du Dashboard Governance. Uniquement des requêtes SQL natives en lecture
 * seule (aucune logique métier, aucune écriture) — même approche que l'espace Finance existant.
 * Toutes les requêtes sont cloisonnées par tenant : soit via {@code tenant_id} en direct, soit
 * par jointure vers {@code trainings.tenant_id} pour les tables du domaine formation qui n'en
 * portent pas (enrollments, training_sessions, prospect_leads).
 */
@Repository
public interface AnalyticsRepository extends JpaRepository<Order, UUID> {

    // ------------------------------------------------------------------ KPI overview
    @Query(value = """
            SELECT
              (SELECT COALESCE(SUM(total_amount),0) FROM orders
                 WHERE tenant_id = :t AND payment_status = 'PAID') AS revenueTotal,
              (SELECT COALESCE(SUM(total_amount),0) FROM orders
                 WHERE tenant_id = :t AND payment_status = 'PAID'
                   AND created_at >= date_trunc('month', now())) AS revenueMonth,
              (SELECT COUNT(*) FROM orders
                 WHERE tenant_id = :t AND payment_status = 'PAID') AS ordersPaid,
              (SELECT COUNT(*) FROM orders
                 WHERE tenant_id = :t AND is_quote = true) AS quotesTotal,
              (SELECT COUNT(DISTINCT user_id) FROM orders
                 WHERE tenant_id = :t AND payment_status = 'PAID') AS payingCustomers,
              (SELECT COUNT(*) FROM users
                 WHERE tenant_id = :t AND deleted_at IS NULL) AS activeUsers,
              (SELECT COUNT(*) FROM users
                 WHERE tenant_id = :t AND created_at >= now() - interval '30 days') AS newUsers30d,
              (SELECT COUNT(*) FROM prospect_leads pl
                 JOIN trainings tr ON tr.id = pl.training_id
                 WHERE tr.tenant_id = :t AND pl.downloaded_at >= now() - interval '30 days') AS leads30d,
              (SELECT COUNT(*) FROM doctor_applications
                 WHERE tenant_id = :t AND status = 'PAID') AS paidApplications,
              (SELECT COUNT(*) FROM enrollments e
                 JOIN training_sessions s ON s.id = e.session_id
                 JOIN trainings tr ON tr.id = s.training_id
                 WHERE tr.tenant_id = :t) AS enrollmentsTotal,
              (SELECT COUNT(*) FROM enrollments e
                 JOIN training_sessions s ON s.id = e.session_id
                 JOIN trainings tr ON tr.id = s.training_id
                 WHERE tr.tenant_id = :t
                   AND e.status IN ('UNDER_OPTIMI_REVIEW','ACTION_REQUIRED','SUBMITTED_TO_PARTNER',
                                    'ACCEPTED_BY_PARTNER','PENDING_TUITION_FEE','CONFIRMED',
                                    'CONVENTION_ISSUED','VISA_SUBMITTED','VISA_GRANTED')) AS enrollmentsActive
            """, nativeQuery = true)
    OverviewRow overview(@Param("t") UUID tenantId);

    // ------------------------------------------------------------------ revenue timeseries
    @Query(value = """
            SELECT d::date AS day,
                   COALESCE(SUM(o.total_amount) FILTER (WHERE o.payment_status = 'PAID'), 0)::numeric AS revenue,
                   COUNT(o.id) FILTER (WHERE o.payment_status = 'PAID') AS orders
            FROM generate_series(
                     date_trunc('day', now()) - ((:days - 1) || ' days')::interval,
                     date_trunc('day', now()),
                     interval '1 day') d
            LEFT JOIN orders o
                   ON o.tenant_id = :t AND date_trunc('day', o.created_at) = d
            GROUP BY d
            ORDER BY d
            """, nativeQuery = true)
    List<DayMoneyRow> revenueTimeseries(@Param("t") UUID tenantId, @Param("days") int days);

    // ------------------------------------------------------------------ user growth timeseries
    @Query(value = """
            SELECT d::date AS day,
                   0::numeric AS revenue,
                   COUNT(u.id) AS orders
            FROM generate_series(
                     date_trunc('day', now()) - ((:days - 1) || ' days')::interval,
                     date_trunc('day', now()),
                     interval '1 day') d
            LEFT JOIN users u
                   ON u.tenant_id = :t AND date_trunc('day', u.created_at) = d
            GROUP BY d
            ORDER BY d
            """, nativeQuery = true)
    List<DayMoneyRow> userGrowthTimeseries(@Param("t") UUID tenantId, @Param("days") int days);

    // ------------------------------------------------------------------ sales by category
    @Query(value = """
            SELECT COALESCE(c.name, '(sans catégorie)') AS label,
                   COALESCE(SUM(oi.subtotal), 0)::numeric AS value,
                   COALESCE(SUM(oi.quantity), 0)::numeric AS secondary
            FROM order_items oi
            JOIN orders o ON o.id = oi.order_id AND o.tenant_id = :t AND o.payment_status = 'PAID'
            LEFT JOIN products p ON p.id = oi.product_id
            LEFT JOIN categories c ON c.id = p.category_id
            GROUP BY c.name
            ORDER BY value DESC
            """, nativeQuery = true)
    List<LabelValueRow> salesByCategory(@Param("t") UUID tenantId);

    // ------------------------------------------------------------------ acquisition funnel
    @Query(value = """
            SELECT
              (SELECT COUNT(*) FROM prospect_leads pl
                 JOIN trainings tr ON tr.id = pl.training_id
                 WHERE tr.tenant_id = :t) AS leads,
              (SELECT COUNT(*) FROM doctor_applications
                 WHERE tenant_id = :t) AS applications,
              (SELECT COUNT(*) FROM doctor_applications
                 WHERE tenant_id = :t AND status = 'PAID') AS applicationsPaid,
              (SELECT COUNT(*) FROM enrollments e
                 JOIN training_sessions s ON s.id = e.session_id
                 JOIN trainings tr ON tr.id = s.training_id
                 WHERE tr.tenant_id = :t) AS enrollments,
              (SELECT COUNT(*) FROM enrollments e
                 JOIN training_sessions s ON s.id = e.session_id
                 JOIN trainings tr ON tr.id = s.training_id
                 WHERE tr.tenant_id = :t
                   AND e.status IN ('CONVENTION_ISSUED','VISA_SUBMITTED','VISA_GRANTED','READY_TO_START')) AS conventionsIssued,
              (SELECT COUNT(*) FROM enrollments e
                 JOIN training_sessions s ON s.id = e.session_id
                 JOIN trainings tr ON tr.id = s.training_id
                 WHERE tr.tenant_id = :t
                   AND e.status IN ('VISA_GRANTED','READY_TO_START')) AS visasGranted
            """, nativeQuery = true)
    FunnelRow acquisitionFunnel(@Param("t") UUID tenantId);

    // ------------------------------------------------------------------ enrollments by status
    @Query(value = """
            SELECT e.status AS label,
                   COUNT(*)::numeric AS value,
                   0::numeric AS secondary
            FROM enrollments e
            JOIN training_sessions s ON s.id = e.session_id
            JOIN trainings tr ON tr.id = s.training_id
            WHERE tr.tenant_id = :t
            GROUP BY e.status
            ORDER BY value DESC
            """, nativeQuery = true)
    List<LabelValueRow> enrollmentsByStatus(@Param("t") UUID tenantId);

    // ------------------------------------------------------------------ top trainings
    @Query(value = """
            SELECT tr.title AS label,
                   COUNT(e.id)::numeric AS value,
                   COALESCE(SUM(s.price) FILTER (WHERE e.id IS NOT NULL), 0)::numeric AS secondary
            FROM trainings tr
            LEFT JOIN training_sessions s ON s.training_id = tr.id
            LEFT JOIN enrollments e ON e.session_id = s.id
            WHERE tr.tenant_id = :t
            GROUP BY tr.id, tr.title
            ORDER BY value DESC, secondary DESC
            LIMIT :limit
            """, nativeQuery = true)
    List<LabelValueRow> topTrainings(@Param("t") UUID tenantId, @Param("limit") int limit);

    // ------------------------------------------------------------------ segments B2B / B2C
    @Query(value = """
            SELECT CASE u.role
                     WHEN 'CLIENT_B2B' THEN 'B2B'
                     WHEN 'CLIENT_B2C' THEN 'B2C'
                     ELSE 'Autre'
                   END AS label,
                   COALESCE(SUM(o.total_amount), 0)::numeric AS value,
                   COUNT(o.id)::numeric AS secondary
            FROM orders o
            JOIN users u ON u.id = o.user_id
            WHERE o.tenant_id = :t AND o.payment_status = 'PAID'
            GROUP BY 1
            ORDER BY value DESC
            """, nativeQuery = true)
    List<LabelValueRow> revenueBySegment(@Param("t") UUID tenantId);

    // ============================================================ projections

    interface OverviewRow {
        BigDecimal getRevenueTotal();
        BigDecimal getRevenueMonth();
        Long getOrdersPaid();
        Long getQuotesTotal();
        Long getPayingCustomers();
        Long getActiveUsers();
        Long getNewUsers30d();
        Long getLeads30d();
        Long getPaidApplications();
        Long getEnrollmentsTotal();
        Long getEnrollmentsActive();
    }

    interface DayMoneyRow {
        LocalDate getDay();
        BigDecimal getRevenue();
        Long getOrders();
    }

    interface LabelValueRow {
        String getLabel();
        BigDecimal getValue();
        BigDecimal getSecondary();
    }

    interface FunnelRow {
        Long getLeads();
        Long getApplications();
        Long getApplicationsPaid();
        Long getEnrollments();
        Long getConventionsIssued();
        Long getVisasGranted();
    }
}
