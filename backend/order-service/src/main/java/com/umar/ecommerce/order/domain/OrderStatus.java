package com.umar.ecommerce.order.domain;

public enum OrderStatus {
    PENDING_STOCK,
    PENDING_HOLD,
    PENDING_PAYMENT,
    PENDING_CONSUMPTION,
    CONFIRMED,
    COMPENSATING,
    CANCEL_PENDING,
    REJECTED,
    CANCELLED,
    MANUAL_REVIEW
}
