package com.umar.ecommerce.inventory.entity;

import com.umar.ecommerce.inventory.domain.ReservationState;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "reservation_history")
public class ReservationHistory {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "reservation_id", nullable = false, updatable = false)
    private UUID reservationId;

    @Column(name = "command_id", nullable = false, updatable = false)
    private UUID commandId;

    @Enumerated(EnumType.STRING)
    @Column(name = "from_state", updatable = false, length = 32)
    private ReservationState fromState;

    @Enumerated(EnumType.STRING)
    @Column(name = "to_state", nullable = false, updatable = false, length = 32)
    private ReservationState toState;

    @Column(name = "detail", nullable = false, updatable = false, length = 300)
    private String detail;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected ReservationHistory() {
    }

    public static ReservationHistory record(
            UUID reservationId,
            UUID commandId,
            ReservationState fromState,
            ReservationState toState,
            String detail
    ) {
        ReservationHistory history = new ReservationHistory();
        history.id = UUID.randomUUID();
        history.reservationId = reservationId;
        history.commandId = commandId;
        history.fromState = fromState;
        history.toState = toState;
        history.detail = detail;
        return history;
    }

    @PrePersist
    void onCreate() {
        createdAt = Instant.now();
    }
}
