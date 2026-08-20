package com.optimisante.backend.domain.orders.dto;

import com.optimisante.backend.domain.orders.entity.PaymentMethod;

import java.util.List;

public record CheckoutRequestDto(
        List<CheckoutItemDto> items,
        PaymentMethod paymentMethod
) {}
