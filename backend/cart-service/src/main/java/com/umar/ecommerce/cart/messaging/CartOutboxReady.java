package com.umar.ecommerce.cart.messaging;

import java.util.UUID;

public record CartOutboxReady(UUID outboxId) {
}
