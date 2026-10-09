package com.umar.ecommerce.order.messaging;

import java.util.UUID;

/**
 * Wake-up published in the same transaction as the outbox row. The listener
 * runs only after commit and must not treat a lost wake-up as a failed order.
 */
public record OutboxReady(UUID outboxId) {
}
