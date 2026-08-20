package com.optimisante.backend.domain.orders.entity;

public enum PaymentStatus {
    UNPAID,
    PAID,
    PENDING_APPROVAL,
    QUOTE_SENT,
    QUOTE_REJECTED
}
