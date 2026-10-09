package com.umar.ecommerce.order.messaging;

import java.util.UUID;

public record OutboxClaim(UUID id, UUID claimToken, String topic, String messageKey, String envelope) {
}
