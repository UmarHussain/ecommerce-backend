package com.umar.ecommerce.cart.dto.response;

import java.math.BigDecimal;

public record CurrencySubtotalResponse(
        String currency,
        BigDecimal amount
) {
}
