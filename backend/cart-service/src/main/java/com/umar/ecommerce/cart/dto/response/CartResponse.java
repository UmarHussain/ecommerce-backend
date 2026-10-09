package com.umar.ecommerce.cart.dto.response;

import com.umar.ecommerce.cart.service.CatalogRefresh;

import java.util.List;

public record CartResponse(
        long version,
        List<CartItemResponse> items,
        List<CurrencySubtotalResponse> subtotals,
        CatalogRefresh catalogRefresh,
        String checkoutNotice
) {
    public static final String CHECKOUT_NOTICE =
            "Prices and stock are validated again at checkout. This cart does not reserve stock.";

    public CartResponse {
        items = List.copyOf(items);
        subtotals = List.copyOf(subtotals);
    }
}
