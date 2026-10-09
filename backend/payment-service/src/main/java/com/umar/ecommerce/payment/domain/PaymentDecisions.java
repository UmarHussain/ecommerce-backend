package com.umar.ecommerce.payment.domain;

/**
 * Charge and refund choices that do not touch a clock, broker, or database.
 * The caller scenario on a command is not an input: only the server scenario is.
 */
public final class PaymentDecisions {

    public enum ChargeReplay {
        REPUBLISH,
        WAIT,
        CONFLICT
    }

    public enum RefundAction {
        APPLY,
        RECORD_FAILURE,
        REPUBLISH,
        CONFLICT
    }

    public record ChargeSelection(AttemptStatus status, String immediateOutcome, boolean scheduleCompletion) {
    }

    public record RefundSelection(RefundAction action, String outcomeType) {
    }

    private PaymentDecisions() {
    }

    public static ChargeSelection selectCharge(ChargeScenario scenario) {
        return switch (scenario) {
            case SUCCESS -> new ChargeSelection(AttemptStatus.SUCCEEDED, "PaymentSucceeded", false);
            case DECLINE -> new ChargeSelection(AttemptStatus.DECLINED, "PaymentDeclined", false);
            case DELAYED_SUCCESS -> new ChargeSelection(AttemptStatus.REQUESTED, null, true);
            case TIMEOUT -> new ChargeSelection(AttemptStatus.UNKNOWN, "PaymentUnknown", false);
            case LATE_SUCCESS -> new ChargeSelection(AttemptStatus.UNKNOWN, "PaymentUnknown", true);
        };
    }

    public static ChargeReplay replayCharge(boolean samePayload, AttemptStatus stored) {
        if (!samePayload) {
            return ChargeReplay.CONFLICT;
        }
        if (stored == AttemptStatus.REQUESTED) {
            return ChargeReplay.WAIT;
        }
        return ChargeReplay.REPUBLISH;
    }

    public static String outcomeForStoredCharge(AttemptStatus stored) {
        return switch (stored) {
            case SUCCEEDED -> "PaymentSucceeded";
            case DECLINED -> "PaymentDeclined";
            case UNKNOWN, REQUESTED -> "PaymentUnknown";
        };
    }

    public static boolean dueCompletionSucceeds(AttemptStatus stored) {
        return stored == AttemptStatus.REQUESTED || stored == AttemptStatus.UNKNOWN;
    }

    public static RefundSelection selectRefund(
            RefundScenario scenario,
            boolean chargeSucceeded,
            boolean matchingAppliedRefund,
            boolean conflictingAppliedRefund,
            RefundStatus stored,
            boolean samePayload
    ) {
        if (stored != null && !samePayload) {
            return new RefundSelection(RefundAction.CONFLICT, "RefundConflict");
        }
        if (stored == RefundStatus.REFUNDED) {
            return new RefundSelection(RefundAction.REPUBLISH, "PaymentRefunded");
        }
        if (conflictingAppliedRefund) {
            return new RefundSelection(RefundAction.CONFLICT, "RefundConflict");
        }
        if (matchingAppliedRefund) {
            return new RefundSelection(RefundAction.REPUBLISH, "PaymentRefunded");
        }
        boolean canApply = scenario == RefundScenario.SUCCESS && chargeSucceeded;
        if (stored == RefundStatus.FAILED || stored == RefundStatus.REQUESTED) {
            if (canApply) {
                return new RefundSelection(RefundAction.APPLY, "PaymentRefunded");
            }
            return new RefundSelection(RefundAction.REPUBLISH, "RefundFailed");
        }
        if (!canApply) {
            return new RefundSelection(RefundAction.RECORD_FAILURE, "RefundFailed");
        }
        return new RefundSelection(RefundAction.APPLY, "PaymentRefunded");
    }
}
