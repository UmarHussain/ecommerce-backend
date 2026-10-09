package com.umar.ecommerce.payment.service;

import java.util.UUID;

public record OutboxReady(UUID outboxId) {
}
