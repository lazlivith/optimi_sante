package com.optimisante.backend.domain.catalog.dto;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
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
        String imageUrl,
        CategorySummaryDto category,
        Boolean isOnPromo,
        OffsetDateTime promoEndsAt,
        /** Formation qui apprend a utiliser l'equipement, {@code null} si aucune. */
        RelatedTrainingDto relatedTraining
) {}
