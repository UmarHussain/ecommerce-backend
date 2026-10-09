package com.umar.ecommerce.inventory.messaging;

public final class InventoryChannels {

    public static final int VERSION = 1;
    public static final String COMMANDS = "checkout.inventory.commands";
    public static final String OUTCOMES = "checkout.inventory.outcomes";
    public static final String GROUP = "inventory-checkout";

    public static final String RESERVE_STOCK = "ReserveStock";
    public static final String HOLD_RESERVATION = "HoldReservation";
    public static final String RELEASE_RESERVATION = "ReleaseReservation";
    public static final String CONSUME_RESERVATION = "ConsumeReservation";
    public static final String RESTOCK_RESERVATION = "RestockReservation";

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

    private InventoryChannels() {
    }
}
