package com.optimisante.backend.domain.orders.repository;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * Projection native pour la liste des ventes récentes (espace Finance admin).
 * getCreatedAt() est typé Instant (pas OffsetDateTime) : le mécanisme de conversion des
 * projections natives de Spring Data JPA ne sait pas convertir Instant -> OffsetDateTime
 * (contrairement au mapping d'entité JPA classique, qui gère cette conversion nativement) —
 * la conversion se fait explicitement dans FinanceService.
 */
public interface RecentSaleRow {
    UUID getOrderId();
    String getOrderNumber();
    String getCustomerEmail();
    BigDecimal getTotalAmount();
    Instant getCreatedAt();
    Long getItemsCount();
}
