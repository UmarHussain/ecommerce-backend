package com.umar.ecommerce.order.domain;

public enum SagaStep {
    AWAIT_RESERVATION,
    AWAIT_HOLD,
    AWAIT_PAYMENT,
    AWAIT_RECONCILE,
    AWAIT_CONSUMPTION,
    AWAIT_CLEANUP,
    AWAIT_RELEASE,
    AWAIT_REFUND,
    AWAIT_RESTOCK,
    COMPLETED,
    MANUAL_REVIEW
}
