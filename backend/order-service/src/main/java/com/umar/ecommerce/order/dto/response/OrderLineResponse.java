package com.umar.ecommerce.order.dto.response;

import java.math.BigDecimal;
import java.util.UUID;

public record OrderLineResponse(
        UUID catalogVariantId,
        String sku,
        String displayName,
        int quantity,
        BigDecimal unitPrice,
        BigDecimal lineTotal
) {
}
