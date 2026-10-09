package com.umar.ecommerce.order.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "order_history")
public class OrderHistory {

    @Id
    @Column(nullable = false, updatable = false)
    private UUID id;

    @Column(name = "order_id", nullable = false, updatable = false)
    private UUID orderId;

    @Column(name = "order_status", nullable = false, length = 32)
    private String orderStatus;

    @Column(name = "payment_status", nullable = false, length = 32)
    private String paymentStatus;

    @Column(name = "fulfilment_status", nullable = false, length = 32)
    private String fulfilmentStatus;

    @Column(name = "saga_step", nullable = false, length = 32)
    private String sagaStep;

    @Column(nullable = false, length = 300)
    private String detail;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected OrderHistory() {
    }

    public static OrderHistory record(CustomerOrder order, String detail, Instant now) {
        OrderHistory history = new OrderHistory();
        history.id = UUID.randomUUID();
        history.orderId = order.getId();
        history.orderStatus = order.getOrderStatus().name();
        history.paymentStatus = order.getPaymentStatus().name();
        history.fulfilmentStatus = order.getFulfilmentStatus().name();
        history.sagaStep = order.getSagaStep().name();
        history.detail = detail.length() > 300 ? detail.substring(0, 300) : detail;
        history.createdAt = now;
        return history;
    }
}
