package com.umar.ecommerce.payment.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.umar.ecommerce.payment.config.PaymentSimulatorProperties;
import com.umar.ecommerce.payment.domain.AttemptStatus;
import com.umar.ecommerce.payment.domain.ChargeScenario;
import com.umar.ecommerce.payment.domain.MoneyRules;
import com.umar.ecommerce.payment.domain.PayloadHash;
import com.umar.ecommerce.payment.domain.PaymentDecisions;
import com.umar.ecommerce.payment.domain.PaymentDecisions.ChargeReplay;
import com.umar.ecommerce.payment.domain.PaymentDecisions.ChargeSelection;
import com.umar.ecommerce.payment.domain.PaymentDecisions.RefundSelection;
import com.umar.ecommerce.payment.domain.RefundScenario;
import com.umar.ecommerce.payment.entity.PaymentAttempt;
import com.umar.ecommerce.payment.entity.PaymentRefund;
import com.umar.ecommerce.payment.entity.SimulatorDue;
import com.umar.ecommerce.payment.messaging.Envelope;
import com.umar.ecommerce.payment.repository.PaymentAttemptRepository;
import com.umar.ecommerce.payment.repository.PaymentRefundRepository;
import com.umar.ecommerce.payment.repository.SimulatorControlRepository;
import com.umar.ecommerce.payment.repository.SimulatorDueRepository;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.Clock;
import java.util.Optional;
import java.util.UUID;

@Service
public class PaymentEffectService {

    private final PaymentAttemptRepository attempts;
    private final PaymentRefundRepository refunds;
    private final SimulatorDueRepository dues;
    private final SimulatorControlRepository controls;
    private final OutcomeWriter outcomes;
    private final PaymentSimulatorProperties properties;
    private final Clock clock;

    public PaymentEffectService(
            PaymentAttemptRepository attempts,
            PaymentRefundRepository refunds,
            SimulatorDueRepository dues,
            SimulatorControlRepository controls,
            OutcomeWriter outcomes,
            PaymentSimulatorProperties properties,
            Clock clock
    ) {
        this.attempts = attempts;
        this.refunds = refunds;
        this.dues = dues;
        this.controls = controls;
        this.outcomes = outcomes;
        this.properties = properties;
        this.clock = clock;
    }

    public void request(Envelope command) {
        JsonNode payload = command.payload();
        UUID orderId = UUID.fromString(payload.get("orderId").asText());
        BigDecimal amount = MoneyRules.amount(payload.get("amount"));
        String currency = MoneyRules.currency(payload.get("currency"));
        UUID operationId = command.commandId();
        String hash = PayloadHash.charge(orderId, amount, currency);
        ChargeSelection selection = PaymentDecisions.selectCharge(chargeScenario());
        PaymentAttempt existing = attempts.lockById(operationId).orElse(null);
        if (existing == null) {
            PaymentAttempt created = PaymentAttempt.create(
                    operationId, orderId, amount, currency, hash, selection.status(), clock.instant(), command);
            attempts.save(created);
            if (selection.scheduleCompletion()) {
                dues.save(SimulatorDue.charge(
                        UUID.randomUUID(),
                        operationId,
                        clock.instant().plus(properties.getDelay())
                ));
            }
            if (selection.immediateOutcome() != null) {
                outcomes.charge(command, created, selection.immediateOutcome());
            }
            return;
        }
        ChargeReplay replay = PaymentDecisions.replayCharge(existing.sameHash(hash), existing.getStatus());
        switch (replay) {
            case CONFLICT -> outcomes.chargeConflict(command, existing);
            case REPUBLISH -> outcomes.charge(command, existing, PaymentDecisions.outcomeForStoredCharge(existing.getStatus()));
            case WAIT -> {
                // The original due row still owns the single completion.
            }
        }
    }

    public void query(Envelope command) {
        JsonNode payload = command.payload();
        UUID orderId = UUID.fromString(payload.get("orderId").asText());
        UUID operationId = UUID.fromString(payload.get("paymentOperationId").asText());
        PaymentAttempt existing = attempts.findById(operationId).orElse(null);
        if (existing == null || !existing.getOrderId().equals(orderId)) {
            return;
        }
        outcomes.charge(command, existing, PaymentDecisions.outcomeForStoredCharge(existing.getStatus()));
    }

    public void refund(Envelope command) {
        JsonNode payload = command.payload();
        UUID orderId = UUID.fromString(payload.get("orderId").asText());
        UUID chargeOperationId = UUID.fromString(payload.get("paymentOperationId").asText());
        BigDecimal amount = MoneyRules.amount(payload.get("amount"));
        String currency = MoneyRules.currency(payload.get("currency"));
        UUID refundOperationId = command.commandId();
        String hash = PayloadHash.refund(orderId, chargeOperationId, amount, currency);
        PaymentAttempt charge = attempts.lockById(chargeOperationId).orElse(null);
        if (charge == null || !charge.getOrderId().equals(orderId)) {
            outcomes.refundFailedWithoutRow(command, orderId, chargeOperationId, refundOperationId, amount, currency);
            return;
        }
        PaymentRefund stored = refunds.lockById(refundOperationId).orElse(null);
        if (stored != null && !stored.getChargeOperationId().equals(chargeOperationId)) {
            outcomes.refundConflict(command, orderId, chargeOperationId, refundOperationId);
            return;
        }
        Optional<PaymentRefund> applied = refunds.findApplied(chargeOperationId);
        boolean matchingApplied = applied.filter(row -> row.getPayloadHash().equals(hash)).isPresent();
        boolean conflictingApplied = applied.filter(row -> !row.getPayloadHash().equals(hash)).isPresent();
        RefundSelection selection = PaymentDecisions.selectRefund(
                refundScenario(),
                charge.getStatus() == AttemptStatus.SUCCEEDED,
                matchingApplied,
                conflictingApplied,
                stored == null ? null : stored.getStatus(),
                stored == null || stored.sameHash(hash)
        );
        switch (selection.action()) {
            case CONFLICT -> outcomes.refundConflict(command, orderId, chargeOperationId, refundOperationId);
            case REPUBLISH -> republish(command, stored, applied, selection.outcomeType());
            case RECORD_FAILURE -> {
                PaymentRefund row = PaymentRefund.failure(
                        refundOperationId, chargeOperationId, orderId, amount, currency, hash, clock.instant());
                refunds.save(row);
                outcomes.refund(command, row, selection.outcomeType());
            }
            case APPLY -> {
                PaymentRefund row = stored;
                if (row == null) {
                    row = PaymentRefund.applied(
                            refundOperationId, chargeOperationId, orderId, amount, currency, hash, clock.instant());
                    refunds.save(row);
                } else {
                    row.applySuccess(clock.instant());
                }
                outcomes.refund(command, row, selection.outcomeType());
            }
        }
    }

    private void republish(Envelope command, PaymentRefund stored, Optional<PaymentRefund> applied, String outcomeType) {
        PaymentRefund source = "PaymentRefunded".equals(outcomeType)
                ? applied.orElse(stored)
                : stored;
        if (source == null) {
            outcomes.refundConflict(command, commandPayloadOrder(command), null, command.commandId());
            return;
        }
        outcomes.refund(command, source, outcomeType);
    }

    private static UUID commandPayloadOrder(Envelope command) {
        return UUID.fromString(command.payload().get("orderId").asText());
    }

    private ChargeScenario chargeScenario() {
        return controls.findById((short) 1)
                .map(row -> row.getChargeOutcome())
                .orElse(properties.getChargeOutcome());
    }

    private RefundScenario refundScenario() {
        return controls.findById((short) 1)
                .map(row -> row.getRefundOutcome())
                .orElse(properties.getRefundOutcome());
    }
}
