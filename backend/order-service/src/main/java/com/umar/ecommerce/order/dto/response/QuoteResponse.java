package com.umar.ecommerce.order.dto.response;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record QuoteResponse(
        UUID id,
        long cartVersion,
        String currency,
        BigDecimal merchandiseTotal,
        BigDecimal shippingTotal,
        BigDecimal taxTotal,
        BigDecimal grandTotal,
        String shippingPolicy,
        String taxPolicy,
        Instant expiresAt,
        AddressSnapshotResponse address,
        List<QuoteLineResponse> lines
) {
}
