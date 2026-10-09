package com.umar.ecommerce.inventory.entity;

import com.umar.ecommerce.inventory.domain.MovementType;
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
@Table(name = "stock_movement")
public class StockMovement {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "stock_item_id", nullable = false, updatable = false)
    private UUID stockItemId;

    @Column(name = "reservation_id", nullable = false, updatable = false)
    private UUID reservationId;

    @Column(name = "command_id", nullable = false, updatable = false)
    private UUID commandId;

    @Enumerated(EnumType.STRING)
    @Column(name = "movement_type", nullable = false, updatable = false, length = 16)
    private MovementType movementType;

    @Column(name = "quantity", nullable = false, updatable = false)
    private int quantity;

    @Column(name = "before_on_hand", nullable = false, updatable = false)
    private int beforeOnHand;

    @Column(name = "after_on_hand", nullable = false, updatable = false)
    private int afterOnHand;

    @Column(name = "before_reserved", nullable = false, updatable = false)
    private int beforeReserved;

    @Column(name = "after_reserved", nullable = false, updatable = false)
    private int afterReserved;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected StockMovement() {
    }

    public static StockMovement record(
            UUID stockItemId,
            UUID reservationId,
            UUID commandId,
            MovementType movementType,
            int quantity,
            int beforeOnHand,
            int afterOnHand,
            int beforeReserved,
            int afterReserved
    ) {
        StockMovement movement = new StockMovement();
        movement.id = UUID.randomUUID();
        movement.stockItemId = stockItemId;
        movement.reservationId = reservationId;
        movement.commandId = commandId;
        movement.movementType = movementType;
        movement.quantity = quantity;
        movement.beforeOnHand = beforeOnHand;
        movement.afterOnHand = afterOnHand;
        movement.beforeReserved = beforeReserved;
        movement.afterReserved = afterReserved;
        return movement;
    }

    @PrePersist
    void onCreate() {
        createdAt = Instant.now();
    }
}
