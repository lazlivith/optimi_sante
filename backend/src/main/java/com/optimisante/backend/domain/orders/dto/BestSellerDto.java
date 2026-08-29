package com.optimisante.backend.domain.orders.dto;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.util.UUID;

@Data
@Builder
public class BestSellerDto {
    private UUID productId;
    private String productName;
    private String imageUrl;
    private Long totalQuantitySold;
    private BigDecimal totalRevenue;
}
