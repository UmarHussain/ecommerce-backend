package com.umar.ecommerce.order.messaging;

public final class CheckoutEvents {

    public static final String RESERVE_STOCK = "ReserveStock";
    public static final String HOLD_RESERVATION = "HoldReservation";
    public static final String RELEASE_RESERVATION = "ReleaseReservation";
    public static final String CONSUME_RESERVATION = "ConsumeReservation";
    public static final String RESTOCK_RESERVATION = "RestockReservation";
    public static final String REQUEST_PAYMENT = "RequestPayment";
    public static final String QUERY_PAYMENT = "QueryPaymentStatus";
    public static final String REFUND_PAYMENT = "RefundPayment";
    public static final String CLEAR_CART = "ClearCheckedOutCart";

    public static final String STOCK_RESERVED = "StockReserved";
    public static final String STOCK_REJECTED = "StockRejected";
    public static final String RESERVATION_HELD = "ReservationHeld";
    public static final String HOLD_REJECTED = "HoldRejected";
    public static final String RESERVATION_EXPIRED = "ReservationExpired";
    public static final String STOCK_RELEASED = "StockReleased";
    public static final String RELEASE_REJECTED = "ReleaseRejected";
    public static final String STOCK_CONSUMED = "StockConsumed";
    public static final String CONSUME_REJECTED = "ConsumeRejected";
    public static final String STOCK_RESTOCKED = "StockRestocked";
    public static final String RESTOCK_REJECTED = "RestockRejected";
    public static final String PAYMENT_SUCCEEDED = "PaymentSucceeded";
    public static final String PAYMENT_DECLINED = "PaymentDeclined";
    public static final String PAYMENT_UNKNOWN = "PaymentUnknown";
    public static final String PAYMENT_CONFLICT = "PaymentConflict";
    public static final String PAYMENT_REFUNDED = "PaymentRefunded";
    public static final String REFUND_FAILED = "RefundFailed";
    public static final String REFUND_CONFLICT = "RefundConflict";
    public static final String CART_CLEARED = "CartCleared";
    public static final String CART_CLEANUP_SKIPPED = "CartCleanupSkipped";

    private CheckoutEvents() {
    }
}
