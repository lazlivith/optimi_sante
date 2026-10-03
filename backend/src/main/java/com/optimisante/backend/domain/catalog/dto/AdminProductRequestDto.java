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
        OffsetDateTime promoEndsAt,
        /** Formation a rattacher, ou {@code null} pour retirer le rattachement. */
        UUID trainingId,

        /**
         * Taux de TVA du produit, en pourcentage. {@code null} : herite de la categorie, puis
         * du taux normal. Laisse a null tant que le comptable n'a pas fourni la table des
         * taux par famille — un taux inscrit d'office serait une declaration fiscale faite
         * par defaut.
         */
        BigDecimal vatRate
) {
}
