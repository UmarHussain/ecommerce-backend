package com.umar.ecommerce.payment.domain;

public enum OutboxStatus {
    PENDING,
    IN_PROGRESS,
    SENT,
    DEAD
}
