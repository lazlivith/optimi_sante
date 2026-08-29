package com.optimisante.backend.domain.catalog.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

public record AdminProductRequestDto(
        @NotBlank String sku,
        @NotBlank String name,
        String description,
        @NotNull BigDecimal basePrice,
        Integer stockQuantity,
        Integer stockThreshold,
        Boolean isQuoteOnly,
        UUID categoryId,
        String imageUrl,
        BigDecimal promoPrice,
        OffsetDateTime promoStartsAt,
        OffsetDateTime promoEndsAt
) {
}
