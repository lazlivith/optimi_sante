package com.optimisante.backend.domain.catalog.repository;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Projection native (pas l'entité Product) utilisée par les requêtes admin qui doivent
 * ignorer @SQLRestriction("deleted_at IS NULL AND is_active = true") — sans cette projection,
 * Hibernate applique la restriction même aux requêtes natives dès que le résultat est mappé
 * sur l'entité Product, rendant impossible la consultation des produits désactivés par l'admin.
 */
public interface AdminProductRow {
    UUID getId();
    String getSku();
    String getName();
    String getSlug();
    String getDescription();
    BigDecimal getBasePrice();
    Integer getStockQuantity();
    Integer getStockThreshold();
    Boolean getIsQuoteOnly();
    Boolean getIsActive();
    String getImageUrl();
    UUID getCategoryId();
    BigDecimal getPromoPrice();
    OffsetDateTime getPromoStartsAt();
    OffsetDateTime getPromoEndsAt();
}
