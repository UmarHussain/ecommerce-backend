package com.umar.ecommerce.inventory.entity;

import com.umar.ecommerce.inventory.domain.ReservationState;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "reservation")
public class Reservation {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "order_id", nullable = false, updatable = false)
    private UUID orderId;

    @Enumerated(EnumType.STRING)
    @Column(name = "state", nullable = false, length = 32)
    private ReservationState state;

    @Column(name = "expires_at")
    private Instant expiresAt;

    @Column(name = "payload_hash", nullable = false, updatable = false, length = 64)
    private String payloadHash;

    @Column(name = "reserve_command_id", nullable = false, updatable = false)
    private UUID reserveCommandId;

    @Version
    @Column(name = "version", nullable = false)
    private long version;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected Reservation() {
    }

    public static Reservation open(UUID orderId, String payloadHash, UUID reserveCommandId, Instant expiresAt) {
        Reservation reservation = new Reservation();
        reservation.id = UUID.randomUUID();
        reservation.orderId = orderId;
        reservation.state = ReservationState.ACTIVE;
        reservation.expiresAt = expiresAt;
        reservation.payloadHash = payloadHash;
        reservation.reserveCommandId = reserveCommandId;
        return reservation;
    }

    public void markHeld() {
        require(ReservationState.ACTIVE);
        this.state = ReservationState.CHECKOUT_HELD;
        this.expiresAt = null;
    }

    public void markReleased() {
        if (state != ReservationState.ACTIVE && state != ReservationState.CHECKOUT_HELD) {
            throw new IllegalStateException("release requires an active or held reservation");
        }
        this.state = ReservationState.RELEASED;
        this.expiresAt = null;
    }

    public void markConsumed() {
        require(ReservationState.CHECKOUT_HELD);
        this.state = ReservationState.CONSUMED;
        this.expiresAt = null;
    }

    public void markRestocked() {
        require(ReservationState.CONSUMED);
        this.state = ReservationState.RESTOCKED;
        this.expiresAt = null;
    }

    @PrePersist
    void onCreate() {
        Instant now = Instant.now();
        createdAt = now;
        updatedAt = now;
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = Instant.now();
    }

    public UUID getId() {
        return id;
    }

    public UUID getOrderId() {
        return orderId;
    }

    public ReservationState getState() {
        return state;
    }

    public Instant getExpiresAt() {
        return expiresAt;
    }

    public String getPayloadHash() {
        return payloadHash;
    }

    public UUID getReserveCommandId() {
        return reserveCommandId;
    }

    public long getVersion() {
        return version;
    }

    private void require(ReservationState expected) {
        if (state != expected) {
            throw new IllegalStateException("reservation is " + state + " and cannot move from " + expected);
        }
    }
}
