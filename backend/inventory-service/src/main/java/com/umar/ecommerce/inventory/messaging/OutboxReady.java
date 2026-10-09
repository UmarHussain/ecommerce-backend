package com.umar.ecommerce.inventory.messaging;

import java.util.UUID;

/**
 * Wake-up published in the same transaction as the outbox row. The listener
 * runs only after commit and must not turn a lost wake-up into a failed reserve.
 */
public record OutboxReady(UUID outboxId) {
}
