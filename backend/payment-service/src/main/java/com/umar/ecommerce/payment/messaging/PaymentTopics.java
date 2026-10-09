package com.umar.ecommerce.payment.messaging;

public final class PaymentTopics {

    public static final String COMMANDS = "checkout.payment.commands";
    public static final String OUTCOMES = "checkout.payment.outcomes";
    public static final String GROUP = "payment-checkout";
    public static final String CONSUMER = "payment-checkout";

    private PaymentTopics() {
    }
}
