package com.umar.ecommerce.payment.entity;

import com.umar.ecommerce.payment.domain.DueAction;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "simulator_due")
public class SimulatorDue {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "operation_id", nullable = false, updatable = false)
    private UUID operationId;

    @Enumerated(EnumType.STRING)
    @Column(name = "action", nullable = false, updatable = false, length = 32)
    private DueAction action;

    @Column(name = "due_at", nullable = false)
    private Instant dueAt;

    @Column(name = "completed", nullable = false)
    private boolean completed;

    protected SimulatorDue() {
    }

    public static SimulatorDue charge(UUID id, UUID operationId, Instant dueAt) {
        SimulatorDue due = new SimulatorDue();
        due.id = id;
        due.operationId = operationId;
        due.action = DueAction.COMPLETE_CHARGE;
        due.dueAt = dueAt;
        due.completed = false;
        return due;
    }

    public void complete() {
        this.completed = true;
    }

    public UUID getId() {
        return id;
    }

    public UUID getOperationId() {
        return operationId;
    }

    public DueAction getAction() {
        return action;
    }

    public Instant getDueAt() {
        return dueAt;
    }

    public boolean isCompleted() {
        return completed;
    }
}
