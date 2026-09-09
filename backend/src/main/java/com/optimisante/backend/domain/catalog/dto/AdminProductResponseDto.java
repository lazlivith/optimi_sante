package com.optimisante.backend.domain.catalog.dto;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

@Data
@Builder
public class AdminProductResponseDto {
    private UUID id;
    private String sku;
    private String name;
    private String slug;
    private String description;
    private BigDecimal basePrice;
    private Integer stockQuantity;
    private Integer stockThreshold;
    private Boolean isQuoteOnly;
    private Boolean isActive;
    private String imageUrl;
    private UUID categoryId;
    private String categoryName;
    private BigDecimal promoPrice;
    private OffsetDateTime promoStartsAt;
    private OffsetDateTime promoEndsAt;

    /** Formation rattachée (offre liée). Sans elle, le formulaire d'édition rouvrirait
     *  toujours sur « Aucune formation liée » et effacerait le rattachement au premier
     *  enregistrement. */
    private java.util.UUID trainingId;
}
