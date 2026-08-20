package com.optimisante.backend.domain.catalog.dto;

import java.math.BigDecimal;
import java.util.UUID;

public record ProductResponseDto(
        UUID id,
        String sku,
        String name,
        String slug,
        String description,
        BigDecimal basePrice,
        BigDecimal finalPrice,
        BigDecimal b2bDiscountRate,
        Integer stockQuantity,
        Boolean isQuoteOnly,
        CategorySummaryDto category
) {}
