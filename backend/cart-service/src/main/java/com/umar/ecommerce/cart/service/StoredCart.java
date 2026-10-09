package com.umar.ecommerce.cart.service;

import com.umar.ecommerce.cart.entity.Cart;

import java.util.List;
import java.util.Optional;

public record StoredCart(long version, List<StoredLine> lines) {

    public StoredCart {
        lines = List.copyOf(lines);
    }

    public static StoredCart empty() {
        return new StoredCart(0, List.of());
    }

    static StoredCart from(Cart cart) {
        return new StoredCart(
                cart.getAggregateVersion(),
                cart.getItems().stream().map(StoredLine::from).toList()
        );
    }

    public Optional<StoredLine> line(String sku) {
        return lines.stream().filter(line -> line.sku().equals(sku)).findFirst();
    }
}
