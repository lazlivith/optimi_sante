package com.optimisante.backend.domain.orders.dto;

import java.util.UUID;

public record CheckoutItemDto(
        UUID productId,
        Integer quantity
) {}
