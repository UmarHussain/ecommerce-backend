package com.umar.ecommerce.payment.messaging;

public final class PaymentEventTypes {

    public static final int VERSION = 1;
    public static final String REQUEST = "RequestPayment";
    public static final String QUERY = "QueryPaymentStatus";
    public static final String REFUND = "RefundPayment";
    public static final String SUCCEEDED = "PaymentSucceeded";
    public static final String DECLINED = "PaymentDeclined";
    public static final String UNKNOWN = "PaymentUnknown";
    public static final String CONFLICT = "PaymentConflict";
    public static final String REFUNDED = "PaymentRefunded";
    public static final String REFUND_FAILED = "RefundFailed";
    public static final String REFUND_CONFLICT = "RefundConflict";

    private PaymentEventTypes() {
    }
}
