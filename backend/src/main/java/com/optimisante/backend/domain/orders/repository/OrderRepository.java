package com.optimisante.backend.domain.orders.repository;

import com.optimisante.backend.domain.orders.entity.Order;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface OrderRepository extends JpaRepository<Order, UUID> {
    Page<Order> findByUserId(UUID userId, Pageable pageable);
    java.util.List<Order> findByUserId(UUID userId);
    Page<Order> findByIsQuoteTrueOrderByCreatedAtDesc(Pageable pageable);

    // --- Espace Finance admin : agrégats en lecture seule, requêtes natives (pas de logique
    // métier, uniquement du reporting) ne portant que sur les commandes réellement payées.
    @Query(value = """
            SELECT
                COALESCE(SUM(o.total_amount), 0) AS totalRevenue,
                COALESCE(SUM(o.total_amount) FILTER (WHERE o.created_at >= date_trunc('day', now())), 0) AS revenueToday,
                COALESCE(SUM(o.total_amount) FILTER (WHERE o.created_at >= date_trunc('month', now())), 0) AS revenueThisMonth,
                COUNT(*) AS ordersCount,
                COUNT(*) FILTER (WHERE o.created_at >= date_trunc('day', now())) AS ordersToday
            FROM orders o
            WHERE o.tenant_id = :tenantId AND o.payment_status = 'PAID'
            """, nativeQuery = true)
    FinanceSummaryRow getFinanceSummary(@Param("tenantId") UUID tenantId);

    @Query(value = """
            SELECT
                oi.product_id AS productId,
                p.name AS productName,
                p.image_url AS imageUrl,
                SUM(oi.quantity) AS totalQuantitySold,
                SUM(oi.subtotal) AS totalRevenue
            FROM order_items oi
            JOIN orders o ON o.id = oi.order_id
            LEFT JOIN products p ON p.id = oi.product_id
            WHERE o.tenant_id = :tenantId AND o.payment_status = 'PAID'
            GROUP BY oi.product_id, p.name, p.image_url
            ORDER BY totalQuantitySold DESC
            LIMIT :limit
            """, nativeQuery = true)
    List<BestSellerRow> getBestSellers(@Param("tenantId") UUID tenantId, @Param("limit") int limit);

    @Query(value = """
            SELECT
                o.id AS orderId,
                o.order_number AS orderNumber,
                u.email AS customerEmail,
                o.total_amount AS totalAmount,
                o.created_at AS createdAt,
                COUNT(oi.id) AS itemsCount
            FROM orders o
            JOIN users u ON u.id = o.user_id
            LEFT JOIN order_items oi ON oi.order_id = o.id
            WHERE o.tenant_id = :tenantId AND o.payment_status = 'PAID'
            GROUP BY o.id, o.order_number, u.email, o.total_amount, o.created_at
            ORDER BY o.created_at DESC
            LIMIT :limit
            """, nativeQuery = true)
    List<RecentSaleRow> getRecentSales(@Param("tenantId") UUID tenantId, @Param("limit") int limit);
}
