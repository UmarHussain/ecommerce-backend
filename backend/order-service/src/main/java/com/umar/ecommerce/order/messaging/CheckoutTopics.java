package com.umar.ecommerce.order.messaging;

public final class CheckoutTopics {

    public static final String INVENTORY_COMMANDS = "checkout.inventory.commands";
    public static final String INVENTORY_OUTCOMES = "checkout.inventory.outcomes";
    public static final String PAYMENT_COMMANDS = "checkout.payment.commands";
    public static final String PAYMENT_OUTCOMES = "checkout.payment.outcomes";
    public static final String CART_COMMANDS = "checkout.cart.commands";
    public static final String CART_OUTCOMES = "checkout.cart.outcomes";

    private CheckoutTopics() {
    }
}
