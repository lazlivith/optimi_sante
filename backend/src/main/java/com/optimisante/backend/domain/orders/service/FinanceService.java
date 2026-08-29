package com.optimisante.backend.domain.orders.service;

import com.optimisante.backend.config.tenant.TenantContext;
import com.optimisante.backend.domain.orders.dto.BestSellerDto;
import com.optimisante.backend.domain.orders.dto.FinanceSummaryDto;
import com.optimisante.backend.domain.orders.dto.RecentSaleDto;
import com.optimisante.backend.domain.orders.repository.BestSellerRow;
import com.optimisante.backend.domain.orders.repository.FinanceSummaryRow;
import com.optimisante.backend.domain.orders.repository.OrderRepository;
import com.optimisante.backend.domain.orders.repository.RecentSaleRow;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Agrégats de reporting pour l'espace Finance admin (ventes, revenus, produits les plus
 * vendus). Lecture seule, ne porte que sur les commandes au statut PAID — aucune écriture,
 * aucun impact sur la logique de commande existante.
 */
@Service
@RequiredArgsConstructor
public class FinanceService {

    private final OrderRepository orderRepository;

    @Transactional(readOnly = true)
    public FinanceSummaryDto getSummary() {
        UUID tenantId = requireTenantId();
        FinanceSummaryRow row = orderRepository.getFinanceSummary(tenantId);

        BigDecimal totalRevenue = row.getTotalRevenue() != null ? row.getTotalRevenue() : BigDecimal.ZERO;
        long ordersCount = row.getOrdersCount() != null ? row.getOrdersCount() : 0L;
        BigDecimal averageOrderValue = ordersCount > 0
                ? totalRevenue.divide(BigDecimal.valueOf(ordersCount), 2, RoundingMode.HALF_UP)
                : BigDecimal.ZERO;

        return FinanceSummaryDto.builder()
                .totalRevenue(totalRevenue)
                .revenueToday(row.getRevenueToday() != null ? row.getRevenueToday() : BigDecimal.ZERO)
                .revenueThisMonth(row.getRevenueThisMonth() != null ? row.getRevenueThisMonth() : BigDecimal.ZERO)
                .ordersCount(ordersCount)
                .ordersToday(row.getOrdersToday() != null ? row.getOrdersToday() : 0L)
                .averageOrderValue(averageOrderValue)
                .build();
    }

    @Transactional(readOnly = true)
    public List<BestSellerDto> getBestSellers(int limit) {
        UUID tenantId = requireTenantId();
        return orderRepository.getBestSellers(tenantId, limit).stream()
                .map(this::toBestSellerDto)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public List<RecentSaleDto> getRecentSales(int limit) {
        UUID tenantId = requireTenantId();
        return orderRepository.getRecentSales(tenantId, limit).stream()
                .map(this::toRecentSaleDto)
                .collect(Collectors.toList());
    }

    private BestSellerDto toBestSellerDto(BestSellerRow row) {
        return BestSellerDto.builder()
                .productId(row.getProductId())
                .productName(row.getProductName())
                .imageUrl(row.getImageUrl())
                .totalQuantitySold(row.getTotalQuantitySold())
                .totalRevenue(row.getTotalRevenue())
                .build();
    }

    private RecentSaleDto toRecentSaleDto(RecentSaleRow row) {
        return RecentSaleDto.builder()
                .orderId(row.getOrderId())
                .orderNumber(row.getOrderNumber())
                .customerEmail(row.getCustomerEmail())
                .totalAmount(row.getTotalAmount())
                .createdAt(row.getCreatedAt() != null ? row.getCreatedAt().atOffset(ZoneOffset.UTC) : null)
                .itemsCount(row.getItemsCount())
                .build();
    }

    private UUID requireTenantId() {
        UUID tenantId = TenantContext.getTenantId();
        if (tenantId == null) {
            throw new IllegalStateException("Tenant context is required");
        }
        return tenantId;
    }
}
