package com.optimisante.backend.domain.orders.repository;

import java.math.BigDecimal;
import java.util.UUID;

/** Projection native pour le classement des produits les plus vendus (espace Finance admin). */
public interface BestSellerRow {
    UUID getProductId();
    String getProductName();
    String getImageUrl();
    Long getTotalQuantitySold();
    BigDecimal getTotalRevenue();
}
