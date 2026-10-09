package com.umar.ecommerce.payment.service;

import java.util.UUID;

public record ClaimedOutbox(
        UUID id,
        UUID claimToken,
        String topic,
        String messageKey,
        String envelope,
        int attempts
) {
}
