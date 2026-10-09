package com.umar.ecommerce.cart.service;

import com.umar.ecommerce.cart.repository.CartRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

@Service
public class CartQueryService {

    private final CartRepository carts;

    public CartQueryService(CartRepository carts) {
        this.carts = carts;
    }

    @Transactional
    public Optional<StoredCart> read(Owner owner) {
        return carts.readByOwner(owner.issuer(), owner.subject()).map(cart -> {
            cart.getItems().size();
            return StoredCart.from(cart);
        });
    }
}
