package com.umar.ecommerce.order.domain;

/**
 * Pure checkout transitions. Callers persist the decision and any outbox row
 * in one database transaction. This class does not read clocks or brokers.
 */
public final class SagaPolicy {

    public static final int MAX_ATTEMPTS = 8;

    private SagaPolicy() {
    }

    public enum SignalType {
        STOCK_RESERVED,
        STOCK_REJECTED,
        RESERVATION_HELD,
        HOLD_REJECTED,
        RESERVATION_EXPIRED,
        PAYMENT_SUCCEEDED,
        PAYMENT_DECLINED,
        PAYMENT_UNKNOWN,
        PAYMENT_CONFLICT,
        STOCK_CONSUMED,
        CONSUME_REJECTED,
        STOCK_RELEASED,
        RELEASE_REJECTED,
        PAYMENT_REFUNDED,
        REFUND_FAILED,
        REFUND_CONFLICT,
        STOCK_RESTOCKED,
        RESTOCK_REJECTED,
        CART_CLEARED,
        CART_CLEANUP_SKIPPED,
        DEADLINE,
        CANCEL_REQUESTED
    }

    public enum Action {
        NONE,
        RESERVE,
        HOLD,
        PAYMENT,
        QUERY_PAYMENT,
        CONSUME,
        RELEASE,
        REFUND,
        RESTOCK,
        CLEANUP
    }

    public record Signal(SignalType type) {
    }

    public record State(
            SagaStep step,
            OrderStatus orderStatus,
            PaymentStatus paymentStatus,
            FulfilmentStatus fulfilmentStatus,
            TerminalPlan plan,
            boolean cancellationRequested,
            boolean stockConsumed,
            int attempts,
            String obligation,
            CleanupStatus cleanupStatus
    ) {
    }

    public record Decision(State state, Action action, boolean ignored, String detail) {
    }

    public static State accepted() {
        return new State(
                SagaStep.AWAIT_RESERVATION,
                OrderStatus.PENDING_STOCK,
                PaymentStatus.NOT_STARTED,
                FulfilmentStatus.NOT_STARTED,
                TerminalPlan.NONE,
                false,
                false,
                0,
                "",
                CleanupStatus.NOT_STARTED
        );
    }

    public static Decision apply(State state, Signal signal) {
        if (signal.type() == SignalType.CANCEL_REQUESTED) {
            return onCancel(state);
        }
        if (state.step() == SagaStep.COMPLETED) {
            return onCompleted(state, signal);
        }
        if (state.step() == SagaStep.MANUAL_REVIEW) {
            return onReview(state, signal);
        }
        if (signal.type() == SignalType.DEADLINE) {
            return onDeadline(state);
        }
        return switch (state.step()) {
            case AWAIT_RESERVATION -> onReservation(state, signal);
            case AWAIT_HOLD -> onHold(state, signal);
            case AWAIT_PAYMENT -> onPayment(state, signal);
            case AWAIT_RECONCILE -> onReconcile(state, signal);
            case AWAIT_CONSUMPTION -> onConsumption(state, signal);
            case AWAIT_CLEANUP -> onCleanup(state, signal);
            case AWAIT_RELEASE -> onRelease(state, signal);
            case AWAIT_REFUND -> onRefund(state, signal);
            case AWAIT_RESTOCK -> onRestock(state, signal);
            default -> ignore(state);
        };
    }

    private static Decision onReservation(State state, Signal signal) {
        return switch (signal.type()) {
            case STOCK_RESERVED -> state.cancellationRequested()
                    ? release(state, TerminalPlan.CANCEL, "cancel after reserve")
                    : move(state, SagaStep.AWAIT_HOLD, OrderStatus.PENDING_HOLD, state.paymentStatus(),
                    state.plan(), Action.HOLD, "stock reserved", "", 0, state.stockConsumed(), state.cleanupStatus());
            case STOCK_REJECTED, RESERVATION_EXPIRED -> rejectNow(state, "stock unavailable");
            default -> ignore(state);
        };
    }

    private static Decision onHold(State state, Signal signal) {
        return switch (signal.type()) {
            case RESERVATION_HELD -> state.cancellationRequested()
                    ? release(state, TerminalPlan.CANCEL, "cancel after hold")
                    : move(state, SagaStep.AWAIT_PAYMENT, OrderStatus.PENDING_PAYMENT, PaymentStatus.REQUESTED,
                    TerminalPlan.NONE, Action.PAYMENT, "reservation held for payment", "", 0, false, state.cleanupStatus());
            case HOLD_REJECTED, RESERVATION_EXPIRED -> rejectNow(state, "reservation was not held");
            default -> ignore(state);
        };
    }

    private static Decision onPayment(State state, Signal signal) {
        return switch (signal.type()) {
            case PAYMENT_SUCCEEDED -> paymentSucceeded(state);
            case PAYMENT_DECLINED -> paymentDeclined(state);
            case PAYMENT_UNKNOWN -> unknown(state, 0, "payment reported unknown");
            case PAYMENT_CONFLICT -> review(state, PaymentStatus.REQUESTED, "payment payload conflict");
            default -> ignore(state);
        };
    }

    private static Decision onReconcile(State state, Signal signal) {
        return switch (signal.type()) {
            case PAYMENT_SUCCEEDED -> paymentSucceeded(state);
            case PAYMENT_DECLINED -> paymentDeclined(state);
            case PAYMENT_UNKNOWN -> bumpUnknown(state);
            case PAYMENT_CONFLICT -> review(state, PaymentStatus.UNKNOWN, "payment payload conflict");
            default -> ignore(state);
        };
    }

    private static Decision onConsumption(State state, Signal signal) {
        return switch (signal.type()) {
            case STOCK_CONSUMED -> state.cancellationRequested()
                    ? move(state, SagaStep.AWAIT_REFUND, OrderStatus.CANCEL_PENDING, PaymentStatus.REFUND_REQUESTED,
                    TerminalPlan.CANCEL, Action.REFUND, "consumed during cancellation", "refund consumed stock", 0, true,
                    state.cleanupStatus())
                    : move(state, SagaStep.AWAIT_CLEANUP, OrderStatus.CONFIRMED, PaymentStatus.SUCCEEDED,
                    TerminalPlan.NONE, Action.CLEANUP, "order confirmed", "", 0, true, CleanupStatus.REQUESTED);
            case CONSUME_REJECTED -> move(state, SagaStep.AWAIT_REFUND, OrderStatus.COMPENSATING, PaymentStatus.REFUND_REQUESTED,
                    TerminalPlan.REJECT, Action.REFUND, "consume rejected", "refund then release", 0, false, state.cleanupStatus());
            default -> ignore(state);
        };
    }

    private static Decision onCleanup(State state, Signal signal) {
        return switch (signal.type()) {
            case CART_CLEARED -> move(state, SagaStep.COMPLETED, OrderStatus.CONFIRMED, state.paymentStatus(),
                    TerminalPlan.NONE, Action.NONE, "cart cleared", "", 0, true, CleanupStatus.CLEARED);
            case CART_CLEANUP_SKIPPED -> move(state, SagaStep.COMPLETED, OrderStatus.CONFIRMED, state.paymentStatus(),
                    TerminalPlan.NONE, Action.NONE, "cart changed; cleanup skipped", "", 0, true, CleanupStatus.SKIPPED);
            default -> ignore(state);
        };
    }

    private static Decision onRelease(State state, Signal signal) {
        return switch (signal.type()) {
            case STOCK_RELEASED -> {
                OrderStatus done = state.plan() == TerminalPlan.CANCEL ? OrderStatus.CANCELLED : OrderStatus.REJECTED;
                yield move(state, SagaStep.COMPLETED, done, state.paymentStatus(), state.plan(), Action.NONE,
                        "reservation released", "", 0, state.stockConsumed(), state.cleanupStatus());
            }
            case RELEASE_REJECTED -> retry(state, Action.RELEASE, "release");
            default -> ignore(state);
        };
    }

    private static Decision onRefund(State state, Signal signal) {
        return switch (signal.type()) {
            case PAYMENT_REFUNDED -> state.stockConsumed()
                    ? move(state, SagaStep.AWAIT_RESTOCK, state.orderStatus(), PaymentStatus.REFUNDED, state.plan(),
                    Action.RESTOCK, "refunded; restock consumed units", "restock", 0, true, state.cleanupStatus())
                    : move(state, SagaStep.AWAIT_RELEASE, state.orderStatus(), PaymentStatus.REFUNDED, state.plan(),
                    Action.RELEASE, "refunded; release held units", "release", 0, false, state.cleanupStatus());
            case REFUND_FAILED -> retry(state, Action.REFUND, "refund");
            case REFUND_CONFLICT -> review(state, PaymentStatus.REFUND_REQUESTED, "refund payload conflict");
            default -> ignore(state);
        };
    }

    private static Decision onRestock(State state, Signal signal) {
        return switch (signal.type()) {
            case STOCK_RESTOCKED -> {
                OrderStatus done = state.plan() == TerminalPlan.CANCEL ? OrderStatus.CANCELLED : OrderStatus.REJECTED;
                yield move(state, SagaStep.COMPLETED, done, PaymentStatus.REFUNDED, state.plan(), Action.NONE,
                        "restock acknowledged", "", 0, true, state.cleanupStatus());
            }
            case RESTOCK_REJECTED -> retry(state, Action.RESTOCK, "restock");
            default -> ignore(state);
        };
    }

    private static Decision onDeadline(State state) {
        return switch (state.step()) {
            case AWAIT_PAYMENT -> unknown(state, 0, "payment timeout is unknown");
            case AWAIT_RECONCILE -> bumpUnknown(state);
            case AWAIT_RESERVATION -> retry(state, Action.RESERVE, "reserve");
            case AWAIT_HOLD -> retry(state, Action.HOLD, "hold");
            case AWAIT_CONSUMPTION -> retry(state, Action.CONSUME, "consume");
            case AWAIT_RELEASE -> retry(state, Action.RELEASE, "release");
            case AWAIT_REFUND -> retry(state, Action.REFUND, "refund");
            case AWAIT_RESTOCK -> retry(state, Action.RESTOCK, "restock");
            case AWAIT_CLEANUP -> retry(state, Action.CLEANUP, "cart cleanup");
            default -> ignore(state);
        };
    }

    private static Decision onCancel(State state) {
        if (state.orderStatus() == OrderStatus.CANCELLED || state.cancellationRequested()) {
            return ignore(state, "already-cancelling");
        }
        if (state.orderStatus() == OrderStatus.REJECTED) {
            return ignore(state, "not-cancellable");
        }
        if (state.orderStatus() == OrderStatus.CONFIRMED || state.step() == SagaStep.AWAIT_CLEANUP) {
            return move(state, SagaStep.AWAIT_REFUND, OrderStatus.CANCEL_PENDING, PaymentStatus.REFUND_REQUESTED,
                    TerminalPlan.CANCEL, Action.REFUND, "cancel confirmed order", "refund then restock", 0, true,
                    state.cleanupStatus(), true);
        }
        if (state.step() == SagaStep.MANUAL_REVIEW) {
            return move(state, SagaStep.MANUAL_REVIEW, OrderStatus.MANUAL_REVIEW, state.paymentStatus(), state.plan(),
                    Action.NONE, "cancellation recorded during review", state.obligation(), state.attempts(),
                    state.stockConsumed(), state.cleanupStatus(), true);
        }
        return move(state, state.step(), OrderStatus.CANCEL_PENDING, state.paymentStatus(), TerminalPlan.CANCEL,
                Action.NONE, "cancellation recorded", state.obligation(), state.attempts(), state.stockConsumed(),
                state.cleanupStatus(), true);
    }

    private static Decision onCompleted(State state, Signal signal) {
        if (state.orderStatus() == OrderStatus.REJECTED
                && signal.type() == SignalType.PAYMENT_SUCCEEDED
                && state.paymentStatus() != PaymentStatus.SUCCEEDED
                && state.paymentStatus() != PaymentStatus.REFUNDED) {
            return move(state, SagaStep.AWAIT_REFUND, OrderStatus.REJECTED, PaymentStatus.REFUND_REQUESTED,
                    TerminalPlan.REJECT, Action.REFUND, "late charge after rejection", "refund late charge", 0,
                    state.stockConsumed(), state.cleanupStatus());
        }
        if (state.orderStatus() == OrderStatus.CONFIRMED && signal.type() == SignalType.PAYMENT_DECLINED) {
            return move(state, SagaStep.MANUAL_REVIEW, OrderStatus.CONFIRMED, state.paymentStatus(), TerminalPlan.NONE,
                    Action.NONE, "contradictory decline after confirmation",
                    "contradictory payment decline; order stays confirmed", state.attempts(), true, state.cleanupStatus());
        }
        return ignore(state);
    }

    private static Decision onReview(State state, Signal signal) {
        if (signal.type() == SignalType.PAYMENT_SUCCEEDED
                || signal.type() == SignalType.PAYMENT_DECLINED
                || signal.type() == SignalType.PAYMENT_UNKNOWN) {
            return onReconcile(state, signal);
        }
        return ignore(state);
    }

    private static Decision paymentSucceeded(State state) {
        if (state.cancellationRequested() || state.plan() == TerminalPlan.CANCEL) {
            return move(state, SagaStep.AWAIT_REFUND, OrderStatus.CANCEL_PENDING, PaymentStatus.REFUND_REQUESTED,
                    TerminalPlan.CANCEL, Action.REFUND, "payment succeeded after cancellation", "refund", 0,
                    state.stockConsumed(), state.cleanupStatus());
        }
        return move(state, SagaStep.AWAIT_CONSUMPTION, OrderStatus.PENDING_CONSUMPTION, PaymentStatus.SUCCEEDED,
                TerminalPlan.NONE, Action.CONSUME, "payment succeeded", "", 0, state.stockConsumed(), state.cleanupStatus());
    }

    private static Decision paymentDeclined(State state) {
        TerminalPlan plan = state.cancellationRequested() ? TerminalPlan.CANCEL : TerminalPlan.REJECT;
        OrderStatus status = state.cancellationRequested() ? OrderStatus.CANCEL_PENDING : OrderStatus.COMPENSATING;
        return move(state, SagaStep.AWAIT_RELEASE, status, PaymentStatus.DECLINED, plan, Action.RELEASE,
                "payment declined", "release reservation", 0, false, state.cleanupStatus());
    }

    private static Decision unknown(State state, int attempts, String detail) {
        return move(state, SagaStep.AWAIT_RECONCILE, OrderStatus.PENDING_PAYMENT, PaymentStatus.UNKNOWN, state.plan(),
                Action.QUERY_PAYMENT, detail, "payment outcome unknown; reservation stays payment-protected", attempts,
                state.stockConsumed(), state.cleanupStatus());
    }

    private static Decision bumpUnknown(State state) {
        int next = state.attempts() + 1;
        if (next >= MAX_ATTEMPTS) {
            return review(state, PaymentStatus.UNKNOWN,
                    "payment outcome unknown; reservation remains payment-protected");
        }
        return unknown(state, next, "requery payment");
    }

    private static Decision retry(State state, Action action, String name) {
        int next = state.attempts() + 1;
        if (next < MAX_ATTEMPTS) {
            return move(state, state.step(), state.orderStatus(), state.paymentStatus(), state.plan(), action,
                    "retry " + name, state.obligation(), next, state.stockConsumed(), state.cleanupStatus());
        }
        if (state.step() == SagaStep.AWAIT_CLEANUP) {
            return move(state, SagaStep.COMPLETED, OrderStatus.CONFIRMED, state.paymentStatus(), TerminalPlan.NONE,
                    Action.NONE, "cleanup unconfirmed; order stays confirmed", "cart cleanup unconfirmed", next, true,
                    CleanupStatus.UNCONFIRMED);
        }
        if (state.step() == SagaStep.AWAIT_REFUND) {
            return review(state, PaymentStatus.REFUND_FAILED, "refund unacknowledged");
        }
        PaymentStatus payment = state.paymentStatus();
        return review(state, payment, name + " unacknowledged");
    }

    private static Decision rejectNow(State state, String detail) {
        return move(state, SagaStep.COMPLETED, OrderStatus.REJECTED, PaymentStatus.NOT_STARTED, TerminalPlan.REJECT,
                Action.NONE, detail, "", 0, false, state.cleanupStatus());
    }

    private static Decision release(State state, TerminalPlan plan, String detail) {
        OrderStatus status = plan == TerminalPlan.CANCEL ? OrderStatus.CANCEL_PENDING : OrderStatus.COMPENSATING;
        return move(state, SagaStep.AWAIT_RELEASE, status, state.paymentStatus(), plan, Action.RELEASE, detail,
                "release reservation", 0, state.stockConsumed(), state.cleanupStatus());
    }

    private static Decision review(State state, PaymentStatus paymentStatus, String reason) {
        OrderStatus orderStatus = state.orderStatus() == OrderStatus.CONFIRMED
                ? OrderStatus.CONFIRMED
                : OrderStatus.MANUAL_REVIEW;
        return move(state, SagaStep.MANUAL_REVIEW, orderStatus, paymentStatus, state.plan(), Action.NONE, reason,
                reason, state.attempts(), state.stockConsumed(), state.cleanupStatus());
    }

    private static Decision ignore(State state) {
        return ignore(state, "ignored");
    }

    private static Decision ignore(State state, String detail) {
        return new Decision(state, Action.NONE, true, detail);
    }

    private static Decision move(
            State state,
            SagaStep step,
            OrderStatus orderStatus,
            PaymentStatus paymentStatus,
            TerminalPlan plan,
            Action action,
            String detail,
            String obligation,
            int attempts,
            boolean stockConsumed,
            CleanupStatus cleanupStatus
    ) {
        return move(state, step, orderStatus, paymentStatus, plan, action, detail, obligation, attempts,
                stockConsumed, cleanupStatus, state.cancellationRequested());
    }

    private static Decision move(
            State state,
            SagaStep step,
            OrderStatus orderStatus,
            PaymentStatus paymentStatus,
            TerminalPlan plan,
            Action action,
            String detail,
            String obligation,
            int attempts,
            boolean stockConsumed,
            CleanupStatus cleanupStatus,
            boolean cancellationRequested
    ) {
        return new Decision(new State(
                step,
                orderStatus,
                paymentStatus,
                state.fulfilmentStatus(),
                plan,
                cancellationRequested,
                stockConsumed,
                attempts,
                obligation,
                cleanupStatus
        ), action, false, detail);
    }
}
