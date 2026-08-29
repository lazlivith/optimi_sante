package com.optimisante.backend.domain.orders.dto;

import com.optimisante.backend.domain.orders.entity.OrderStatus;
import com.optimisante.backend.domain.orders.entity.PaymentMethod;
import com.optimisante.backend.domain.orders.entity.PaymentStatus;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

public record OrderResponseDto(
        UUID id,
        String orderNumber,
        PaymentMethod paymentMethod,
        PaymentStatus paymentStatus,
        OrderStatus status,
        Boolean isQuote,
        BigDecimal totalAmount,
        String stripePaymentIntentId,
        String stripeCheckoutSessionId,
        String paymentUrl,
        String clientSecret,
        String documentS3Key,
        String promoCode,
        BigDecimal discountAmount,
        OffsetDateTime createdAt,
        List<OrderItemDto> items
) {}
