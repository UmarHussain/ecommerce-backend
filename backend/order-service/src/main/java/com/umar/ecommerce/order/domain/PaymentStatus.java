package com.umar.ecommerce.order.domain;

public enum PaymentStatus {
    NOT_STARTED,
    REQUESTED,
    SUCCEEDED,
    DECLINED,
    UNKNOWN,
    REFUND_REQUESTED,
    REFUNDED,
    REFUND_FAILED
}
