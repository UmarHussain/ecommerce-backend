package com.umar.ecommerce.payment.entity;

import com.umar.ecommerce.payment.domain.ChargeScenario;
import com.umar.ecommerce.payment.domain.RefundScenario;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

@Entity
@Table(name = "simulator_control")
public class SimulatorControl {

    @Id
    @Column(name = "id", nullable = false)
    private Short id;

    @Enumerated(EnumType.STRING)
    @Column(name = "charge_outcome", nullable = false, length = 32)
    private ChargeScenario chargeOutcome;

    @Enumerated(EnumType.STRING)
    @Column(name = "refund_outcome", nullable = false, length = 32)
    private RefundScenario refundOutcome;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected SimulatorControl() {
    }

    public Short getId() {
        return id;
    }

    public ChargeScenario getChargeOutcome() {
        return chargeOutcome;
    }

    public RefundScenario getRefundOutcome() {
        return refundOutcome;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
