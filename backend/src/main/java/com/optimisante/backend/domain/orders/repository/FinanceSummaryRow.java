package com.optimisante.backend.domain.orders.repository;

import java.math.BigDecimal;

/** Projection native pour l'agrégat de revenus de l'espace Finance admin. */
public interface FinanceSummaryRow {
    BigDecimal getTotalRevenue();
    BigDecimal getRevenueToday();
    BigDecimal getRevenueThisMonth();
    Long getOrdersCount();
    Long getOrdersToday();
}
