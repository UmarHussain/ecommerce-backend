package com.umar.ecommerce.order.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "checkout_request")
public class CheckoutRequest {

    public static final String IN_PROGRESS = "IN_PROGRESS";
    public static final String COMPLETED = "COMPLETED";

    @Id
    @Column(nullable = false, updatable = false)
    private UUID id;

    @Column(name = "owner_issuer", nullable = false, updatable = false, length = 500)
    private String ownerIssuer;

    @Column(name = "owner_subject", nullable = false, updatable = false, length = 255)
    private String ownerSubject;

    @Column(name = "idempotency_key", nullable = false, updatable = false, length = 128)
    private String idempotencyKey;

    @Column(name = "request_fingerprint", nullable = false, updatable = false, length = 64)
    private String requestFingerprint;

    @Column(name = "request_status", nullable = false, length = 20)
    private String requestStatus;

    @Column(name = "http_status")
    private Integer httpStatus;

    @Column(name = "response_body")
    private String responseBody;

    @Column(name = "order_id")
    private UUID orderId;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "completed_at")
    private Instant completedAt;

    protected CheckoutRequest() {
    }

    public static CheckoutRequest start(
            UUID id,
            String issuer,
            String subject,
            String idempotencyKey,
            String fingerprint,
            Instant now
    ) {
        CheckoutRequest request = new CheckoutRequest();
        request.id = id;
        request.ownerIssuer = issuer;
        request.ownerSubject = subject;
        request.idempotencyKey = idempotencyKey;
        request.requestFingerprint = fingerprint;
        request.requestStatus = IN_PROGRESS;
        request.createdAt = now;
        return request;
    }

    public void complete(int status, String body, UUID orderId, Instant now) {
        this.requestStatus = COMPLETED;
        this.httpStatus = status;
        this.responseBody = body;
        this.orderId = orderId;
        this.completedAt = now;
    }

    public UUID getId() {
        return id;
    }

    public String getRequestFingerprint() {
        return requestFingerprint;
    }

    public String getRequestStatus() {
        return requestStatus;
    }

    public Integer getHttpStatus() {
        return httpStatus;
    }

    public String getResponseBody() {
        return responseBody;
    }

    public UUID getOrderId() {
        return orderId;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
