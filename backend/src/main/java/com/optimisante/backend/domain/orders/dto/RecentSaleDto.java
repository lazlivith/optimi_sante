package com.optimisante.backend.domain.orders.dto;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

@Data
@Builder
public class RecentSaleDto {
    private UUID orderId;
    private String orderNumber;
    private String customerEmail;
    private BigDecimal totalAmount;
    private OffsetDateTime createdAt;
    private Long itemsCount;
}
