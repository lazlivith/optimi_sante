package com.optimisante.backend.domain.orders.dto;

import java.util.List;

public record QuoteRequestDto(
        List<CheckoutItemDto> items,
        String notes
) {}
