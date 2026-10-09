package com.umar.ecommerce.payment.domain;

import com.fasterxml.jackson.databind.node.DecimalNode;
import com.fasterxml.jackson.databind.node.TextNode;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class PaymentDecisionsTest {

    @Test
    void serverScenarioSelectsTheChargeEffect() {
        assertThat(PaymentDecisions.selectCharge(ChargeScenario.SUCCESS))
                .isEqualTo(new PaymentDecisions.ChargeSelection(AttemptStatus.SUCCEEDED, "PaymentSucceeded", false));
        assertThat(PaymentDecisions.selectCharge(ChargeScenario.DECLINE))
                .isEqualTo(new PaymentDecisions.ChargeSelection(AttemptStatus.DECLINED, "PaymentDeclined", false));
        assertThat(PaymentDecisions.selectCharge(ChargeScenario.DELAYED_SUCCESS))
                .isEqualTo(new PaymentDecisions.ChargeSelection(AttemptStatus.REQUESTED, null, true));
        assertThat(PaymentDecisions.selectCharge(ChargeScenario.TIMEOUT))
                .isEqualTo(new PaymentDecisions.ChargeSelection(AttemptStatus.UNKNOWN, "PaymentUnknown", false));
        assertThat(PaymentDecisions.selectCharge(ChargeScenario.LATE_SUCCESS))
                .isEqualTo(new PaymentDecisions.ChargeSelection(AttemptStatus.UNKNOWN, "PaymentUnknown", true));
    }

    @Test
    void samePayloadReplaysAndADifferentPayloadConflicts() {
        assertThat(PaymentDecisions.replayCharge(true, AttemptStatus.SUCCEEDED)).isEqualTo(PaymentDecisions.ChargeReplay.REPUBLISH);
        assertThat(PaymentDecisions.replayCharge(true, AttemptStatus.DECLINED)).isEqualTo(PaymentDecisions.ChargeReplay.REPUBLISH);
        assertThat(PaymentDecisions.replayCharge(true, AttemptStatus.UNKNOWN)).isEqualTo(PaymentDecisions.ChargeReplay.REPUBLISH);
        assertThat(PaymentDecisions.replayCharge(true, AttemptStatus.REQUESTED)).isEqualTo(PaymentDecisions.ChargeReplay.WAIT);
        assertThat(PaymentDecisions.replayCharge(false, AttemptStatus.SUCCEEDED)).isEqualTo(PaymentDecisions.ChargeReplay.CONFLICT);
        assertThat(PaymentDecisions.outcomeForStoredCharge(AttemptStatus.REQUESTED)).isEqualTo("PaymentUnknown");
        assertThat(PaymentDecisions.outcomeForStoredCharge(AttemptStatus.UNKNOWN)).isEqualTo("PaymentUnknown");
    }

    @Test
    void dueCompletionOnlyFinishesAnOpenUnknownOrRequestedCharge() {
        assertThat(PaymentDecisions.dueCompletionSucceeds(AttemptStatus.REQUESTED)).isTrue();
        assertThat(PaymentDecisions.dueCompletionSucceeds(AttemptStatus.UNKNOWN)).isTrue();
        assertThat(PaymentDecisions.dueCompletionSucceeds(AttemptStatus.SUCCEEDED)).isFalse();
        assertThat(PaymentDecisions.dueCompletionSucceeds(AttemptStatus.DECLINED)).isFalse();
    }

    @Test
    void refundAppliesOnceAndAFailedAttemptCanBeAppliedLater() {
        assertThat(refund(RefundScenario.SUCCESS, true, false, false, null, true).action())
                .isEqualTo(PaymentDecisions.RefundAction.APPLY);
        assertThat(refund(RefundScenario.REFUND_FAILURE, true, false, false, null, true))
                .isEqualTo(new PaymentDecisions.RefundSelection(PaymentDecisions.RefundAction.RECORD_FAILURE, "RefundFailed"));
        assertThat(refund(RefundScenario.SUCCESS, false, false, false, null, true).outcomeType())
                .isEqualTo("RefundFailed");
        assertThat(refund(RefundScenario.SUCCESS, true, false, false, RefundStatus.REFUNDED, true).action())
                .isEqualTo(PaymentDecisions.RefundAction.REPUBLISH);
        assertThat(refund(RefundScenario.SUCCESS, true, false, false, RefundStatus.REFUNDED, false).action())
                .isEqualTo(PaymentDecisions.RefundAction.CONFLICT);
        assertThat(refund(RefundScenario.SUCCESS, true, false, false, RefundStatus.FAILED, true).action())
                .isEqualTo(PaymentDecisions.RefundAction.APPLY);
        assertThat(refund(RefundScenario.REFUND_FAILURE, true, false, false, RefundStatus.FAILED, true).outcomeType())
                .isEqualTo("RefundFailed");
        assertThat(refund(RefundScenario.SUCCESS, true, true, false, null, true).action())
                .isEqualTo(PaymentDecisions.RefundAction.REPUBLISH);
        assertThat(refund(RefundScenario.SUCCESS, true, false, true, null, true).outcomeType())
                .isEqualTo("RefundConflict");
    }

    @Test
    void normalizedAmountsShareOneChargeHash() {
        UUID orderId = UUID.fromString("10000000-0000-0000-0000-000000000001");
        BigDecimal text = MoneyRules.amount(TextNode.valueOf("10.50"));
        BigDecimal number = MoneyRules.amount(DecimalNode.valueOf(new BigDecimal("10.5")));
        assertThat(text).isEqualByComparingTo("10.50");
        assertThat(PayloadHash.charge(orderId, text, "USD")).isEqualTo(PayloadHash.charge(orderId, number, "USD"));
        assertThat(PayloadHash.charge(orderId, text, "USD")).hasSize(64);
    }

    private static PaymentDecisions.RefundSelection refund(
            RefundScenario scenario,
            boolean chargeSucceeded,
            boolean matchingApplied,
            boolean conflictingApplied,
            RefundStatus stored,
            boolean samePayload
    ) {
        return PaymentDecisions.selectRefund(scenario, chargeSucceeded, matchingApplied, conflictingApplied, stored, samePayload);
    }
}
