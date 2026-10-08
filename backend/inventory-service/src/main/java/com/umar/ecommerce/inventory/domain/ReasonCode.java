package com.umar.ecommerce.inventory.domain;

import com.umar.ecommerce.inventory.exception.InventoryProblem;
import org.springframework.http.HttpStatus;

/**
 * Opening balance is setup-only and may be zero. Adjustments are never zero.
 * Receipts and returns increase on-hand. Damage and loss decrease it.
 * Corrections may move either direction.
 */
public enum ReasonCode {
    OPENING_BALANCE,
    INBOUND_RECEIPT,
    CORRECTION,
    DAMAGE_LOSS,
    RETURN;

    public void requireForSetup(int initialOnHand) {
        if (this != OPENING_BALANCE) {
            throw validation("Opening stock uses reason OPENING_BALANCE");
        }
        if (initialOnHand < 0) {
            throw validation("Initial on-hand must be zero or greater");
        }
    }

    public void requireForAdjustment(int delta) {
        if (this == OPENING_BALANCE) {
            throw validation("OPENING_BALANCE is only valid when setting up stock");
        }
        if (delta == 0) {
            throw validation("An adjustment quantity must be non-zero");
        }
        switch (this) {
            case INBOUND_RECEIPT, RETURN -> {
                if (delta <= 0) {
                    throw validation(name() + " requires a positive quantity");
                }
            }
            case DAMAGE_LOSS -> {
                if (delta >= 0) {
                    throw validation("DAMAGE_LOSS requires a negative quantity");
                }
            }
            case CORRECTION -> {
                // Either sign is allowed. Zero is already rejected.
            }
            default -> throw validation("Unsupported adjustment reason");
        }
    }

    private static InventoryProblem validation(String message) {
        return new InventoryProblem(HttpStatus.BAD_REQUEST, InventoryProblem.VALIDATION_FAILED, message);
    }
}
