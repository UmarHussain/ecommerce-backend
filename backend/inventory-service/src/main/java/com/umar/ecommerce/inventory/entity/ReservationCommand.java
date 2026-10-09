package com.umar.ecommerce.inventory.entity;

import com.umar.ecommerce.inventory.domain.ReservationCommandType;
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
@Table(name = "reservation_command")
public class ReservationCommand {

    @Id
    @Column(name = "command_id", nullable = false, updatable = false)
    private UUID commandId;

    @Column(name = "order_id", nullable = false, updatable = false)
    private UUID orderId;

    @Enumerated(EnumType.STRING)
    @Column(name = "command_type", nullable = false, updatable = false, length = 32)
    private ReservationCommandType commandType;

    @Column(name = "payload_hash", nullable = false, updatable = false, length = 64)
    private String payloadHash;

    @Column(name = "result_event_type", nullable = false, updatable = false, length = 40)
    private String resultEventType;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected ReservationCommand() {
    }

    public static ReservationCommand recorded(
            UUID commandId,
            UUID orderId,
            ReservationCommandType commandType,
            String payloadHash,
            String resultEventType
    ) {
        ReservationCommand command = new ReservationCommand();
        command.commandId = commandId;
        command.orderId = orderId;
        command.commandType = commandType;
        command.payloadHash = payloadHash;
        command.resultEventType = resultEventType;
        return command;
    }

    @PrePersist
    void onCreate() {
        createdAt = Instant.now();
    }

    public String getPayloadHash() {
        return payloadHash;
    }

    public String getResultEventType() {
        return resultEventType;
    }
}
