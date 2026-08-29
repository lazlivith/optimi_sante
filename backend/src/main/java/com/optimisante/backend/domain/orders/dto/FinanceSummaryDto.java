package com.optimisante.backend.domain.orders.dto;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;

@Data
@Builder
public class FinanceSummaryDto {
    private BigDecimal totalRevenue;
    private BigDecimal revenueToday;
    private BigDecimal revenueThisMonth;
    private Long ordersCount;
    private Long ordersToday;
    private BigDecimal averageOrderValue;
}
