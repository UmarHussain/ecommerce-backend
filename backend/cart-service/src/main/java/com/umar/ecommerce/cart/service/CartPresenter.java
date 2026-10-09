package com.umar.ecommerce.cart.service;

import com.umar.ecommerce.cart.catalog.CatalogVariant;
import com.umar.ecommerce.cart.dto.response.CartItemResponse;
import com.umar.ecommerce.cart.dto.response.CartResponse;
import com.umar.ecommerce.cart.dto.response.CurrencySubtotalResponse;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class CartPresenter {

    private final CartMapper mapper;

    public CartPresenter(CartMapper mapper) {
        this.mapper = mapper;
    }

    public CartResponse present(StoredCart cart, CatalogRefresh refresh, Map<String, CatalogVariant> live) {
        List<CartItemResponse> items = new ArrayList<>();
        Map<String, BigDecimal> totals = new LinkedHashMap<>();
        for (StoredLine line : cart.lines()) {
            CatalogLineState state = CatalogLineState.UNKNOWN;
            BigDecimal current = null;
            if (refresh == CatalogRefresh.FRESH && live != null) {
                CatalogVariant variant = live.get(line.sku());
                if (variant == null) {
                    state = CatalogLineState.UNAVAILABLE;
                } else {
                    state = CatalogLineState.CONFIRMED;
                    current = variant.price();
                }
            }
            items.add(mapper.toItem(line, state, current));
            totals.merge(line.currency(), line.lineTotal(), BigDecimal::add);
        }
        List<CurrencySubtotalResponse> subtotals = totals.entrySet().stream()
                .sorted(Map.Entry.comparingByKey(Comparator.naturalOrder()))
                .map(entry -> new CurrencySubtotalResponse(entry.getKey(), entry.getValue()))
                .toList();
        return new CartResponse(cart.version(), items, subtotals, refresh, CartResponse.CHECKOUT_NOTICE);
    }
}
