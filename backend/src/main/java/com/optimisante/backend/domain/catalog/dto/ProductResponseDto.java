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
        RelatedTrainingDto relatedTraining,

        /**
         * Video de demonstration. Presente aussi bien en liste qu'en fiche : les cartes de
         * promotion doivent pouvoir s'animer sans relire chaque produit.
         */
        String videoUrl,
        String videoProvider,
        Boolean isVideoPromoted,

        /**
         * Visuels secondaires, <b>renseignes uniquement sur la fiche detaillee</b>.
         *
         * <p>Les charger en liste declencherait une requete par produit — le defaut mesure et
         * corrige a plusieurs reprises sur ce projet. Une liste de catalogue n'affiche de toute
         * facon qu'une vignette : la galerie n'y sert a rien.</p>
         */
        java.util.List<ProductMediaDtos.GalleryImageView> gallery
) {}
