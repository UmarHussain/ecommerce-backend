package com.umar.ecommerce.order.domain;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SagaPolicyTest {

    @Test
    void happyPathConfirmsOnlyAfterPaymentAndConsume() {
        SagaPolicy.Decision reserved = apply(SagaPolicy.accepted(), SagaPolicy.SignalType.STOCK_RESERVED);
        assertEquals(SagaPolicy.Action.HOLD, reserved.action());
        SagaPolicy.State state = reserved.state();
        assertEquals(SagaPolicy.Action.PAYMENT, apply(state, SagaPolicy.SignalType.RESERVATION_HELD).action());
        state = apply(state, SagaPolicy.SignalType.RESERVATION_HELD).state();
        assertEquals(OrderStatus.PENDING_PAYMENT, state.orderStatus());
        state = apply(state, SagaPolicy.SignalType.PAYMENT_SUCCEEDED).state();
        assertEquals(OrderStatus.PENDING_CONSUMPTION, state.orderStatus());
        SagaPolicy.Decision confirmed = apply(state, SagaPolicy.SignalType.STOCK_CONSUMED);
        assertEquals(OrderStatus.CONFIRMED, confirmed.state().orderStatus());
        assertEquals(SagaPolicy.Action.CLEANUP, confirmed.action());
        SagaPolicy.Decision done = apply(confirmed.state(), SagaPolicy.SignalType.CART_CLEANUP_SKIPPED);
        assertEquals(SagaStep.COMPLETED, done.state().step());
        assertEquals(OrderStatus.CONFIRMED, done.state().orderStatus());
        assertEquals(CleanupStatus.SKIPPED, done.state().cleanupStatus());
    }

    @Test
    void stockRejectionDoesNotStartPayment() {
        SagaPolicy.Decision decision = apply(SagaPolicy.accepted(), SagaPolicy.SignalType.STOCK_REJECTED);
        assertEquals(OrderStatus.REJECTED, decision.state().orderStatus());
        assertEquals(PaymentStatus.NOT_STARTED, decision.state().paymentStatus());
        assertEquals(SagaPolicy.Action.NONE, decision.action());
    }

    @Test
    void paymentTimeoutIsUnknownAndDoesNotRelease() {
        SagaPolicy.State paying = apply(apply(SagaPolicy.accepted(), SagaPolicy.SignalType.STOCK_RESERVED).state(),
                SagaPolicy.SignalType.RESERVATION_HELD).state();
        SagaPolicy.Decision timedOut = apply(paying, SagaPolicy.SignalType.DEADLINE);
        assertEquals(PaymentStatus.UNKNOWN, timedOut.state().paymentStatus());
        assertEquals(SagaPolicy.Action.QUERY_PAYMENT, timedOut.action());
        assertEquals(SagaStep.AWAIT_RECONCILE, timedOut.state().step());
    }

    @Test
    void declineReleasesBeforeRejection() {
        SagaPolicy.State paying = apply(apply(SagaPolicy.accepted(), SagaPolicy.SignalType.STOCK_RESERVED).state(),
                SagaPolicy.SignalType.RESERVATION_HELD).state();
        SagaPolicy.Decision declined = apply(paying, SagaPolicy.SignalType.PAYMENT_DECLINED);
        assertEquals(SagaStep.AWAIT_RELEASE, declined.state().step());
        assertEquals(PaymentStatus.DECLINED, declined.state().paymentStatus());
        SagaPolicy.Decision released = apply(declined.state(), SagaPolicy.SignalType.STOCK_RELEASED);
        assertEquals(OrderStatus.REJECTED, released.state().orderStatus());
    }

    @Test
    void cancelDuringPaymentRefundsALateSuccess() {
        SagaPolicy.State paying = apply(apply(SagaPolicy.accepted(), SagaPolicy.SignalType.STOCK_RESERVED).state(),
                SagaPolicy.SignalType.RESERVATION_HELD).state();
        SagaPolicy.State cancelling = apply(paying, SagaPolicy.SignalType.CANCEL_REQUESTED).state();
        assertEquals(OrderStatus.CANCEL_PENDING, cancelling.orderStatus());
        assertEquals(SagaStep.AWAIT_PAYMENT, cancelling.step());
        SagaPolicy.Decision late = apply(cancelling, SagaPolicy.SignalType.PAYMENT_SUCCEEDED);
        assertEquals(SagaPolicy.Action.REFUND, late.action());
        assertFalse(late.state().orderStatus() == OrderStatus.CONFIRMED);
    }

    @Test
    void confirmedCancelRefundsThenRestocksBeforeCancelled() {
        SagaPolicy.State confirmed = confirmedState();
        SagaPolicy.Decision cancel = apply(confirmed, SagaPolicy.SignalType.CANCEL_REQUESTED);
        assertEquals(SagaPolicy.Action.REFUND, cancel.action());
        assertTrue(cancel.state().cancellationRequested());
        SagaPolicy.Decision repeat = apply(cancel.state(), SagaPolicy.SignalType.CANCEL_REQUESTED);
        assertTrue(repeat.ignored());
        assertEquals(SagaPolicy.Action.NONE, repeat.action());
        SagaPolicy.Decision refunded = apply(cancel.state(), SagaPolicy.SignalType.PAYMENT_REFUNDED);
        assertEquals(SagaPolicy.Action.RESTOCK, refunded.action());
        SagaPolicy.Decision done = apply(refunded.state(), SagaPolicy.SignalType.STOCK_RESTOCKED);
        assertEquals(OrderStatus.CANCELLED, done.state().orderStatus());
        assertTrue(done.state().cancellationRequested());
    }

    @Test
    void lateChargeAfterRejectionRefundsAndDoesNotConfirm() {
        SagaPolicy.State rejected = apply(SagaPolicy.accepted(), SagaPolicy.SignalType.STOCK_REJECTED).state();
        SagaPolicy.Decision late = apply(rejected, SagaPolicy.SignalType.PAYMENT_SUCCEEDED);
        assertEquals(SagaPolicy.Action.REFUND, late.action());
        assertEquals(OrderStatus.REJECTED, late.state().orderStatus());
    }

    @Test
    void exhaustedUnknownPaymentStaysInReviewWithoutRelease() {
        SagaPolicy.State unknown = apply(apply(apply(SagaPolicy.accepted(), SagaPolicy.SignalType.STOCK_RESERVED).state(),
                SagaPolicy.SignalType.RESERVATION_HELD).state(), SagaPolicy.SignalType.DEADLINE).state();
        SagaPolicy.State current = unknown;
        for (int i = 0; i < SagaPolicy.MAX_ATTEMPTS; i++) {
            current = apply(current, SagaPolicy.SignalType.DEADLINE).state();
        }
        assertEquals(OrderStatus.MANUAL_REVIEW, current.orderStatus());
        assertEquals(PaymentStatus.UNKNOWN, current.paymentStatus());
        assertTrue(current.obligation().contains("payment-protected"));
    }

    @Test
    void duplicateSuccessAfterConfirmIsIgnored() {
        SagaPolicy.Decision duplicate = apply(confirmedState(), SagaPolicy.SignalType.STOCK_CONSUMED);
        assertTrue(duplicate.ignored());
        assertEquals(OrderStatus.CONFIRMED, duplicate.state().orderStatus());
    }

    private static SagaPolicy.State confirmedState() {
        SagaPolicy.State state = SagaPolicy.accepted();
        state = apply(state, SagaPolicy.SignalType.STOCK_RESERVED).state();
        state = apply(state, SagaPolicy.SignalType.RESERVATION_HELD).state();
        state = apply(state, SagaPolicy.SignalType.PAYMENT_SUCCEEDED).state();
        state = apply(state, SagaPolicy.SignalType.STOCK_CONSUMED).state();
        return apply(state, SagaPolicy.SignalType.CART_CLEARED).state();
    }

    private static SagaPolicy.Decision apply(SagaPolicy.State state, SagaPolicy.SignalType type) {
        return SagaPolicy.apply(state, new SagaPolicy.Signal(type));
    }
}
