package com.umar.ecommerce.cart.service;

import com.umar.ecommerce.cart.catalog.CatalogVariant;
import com.umar.ecommerce.cart.entity.Cart;
import com.umar.ecommerce.cart.entity.CartItem;
import com.umar.ecommerce.cart.exception.CartProblem;
import com.umar.ecommerce.cart.repository.CartRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;

@Service
public class CartTransactionService {

    private final CartRepository carts;
    private final Clock clock;

    public CartTransactionService(CartRepository carts, Clock clock) {
        this.carts = carts;
        this.clock = clock;
    }

    @Transactional
    public StoredCart createEmpty(Owner owner) {
        Cart cart = carts.saveAndFlush(Cart.create(owner.issuer(), owner.subject(), Instant.now(clock)));
        return StoredCart.from(cart);
    }

    @Transactional
    public StoredCart setQuantity(
            Owner owner,
            String sku,
            int quantity,
            long expectedVersion,
            CatalogVariant snapshot
    ) {
        Cart cart = lockOrCreate(owner, expectedVersion);
        CartItem line = cart.line(sku);
        if (line != null && line.getQuantity() == quantity) {
            return StoredCart.from(cart);
        }
        boolean adding = line == null;
        boolean increasing = line != null && quantity > line.getQuantity();
        if ((adding || increasing) && snapshot == null) {
            throw new CartProblem(
                    HttpStatus.CONFLICT,
                    CartProblem.REVALIDATION_REQUIRED,
                    "The cart changed while catalog was being checked; reload and review"
            );
        }
        Instant now = Instant.now(clock);
        if (adding) {
            if (snapshot == null || !sku.equals(snapshot.sku())) {
                throw new CartProblem(
                        HttpStatus.CONFLICT,
                        CartProblem.SKU_UNAVAILABLE,
                        "Catalog did not confirm that SKU"
                );
            }
            cart.addItem(CartItem.create(cart, snapshot, quantity, now));
        } else if (increasing) {
            line.replaceSnapshot(snapshot, now);
            line.setQuantity(quantity);
        } else {
            line.setQuantity(quantity);
        }
        cart.advance(now);
        carts.saveAndFlush(cart);
        return StoredCart.from(cart);
    }

    @Transactional
    public StoredCart remove(Owner owner, String sku, long expectedVersion) {
        Cart cart = requireLocked(owner, expectedVersion);
        CartItem line = cart.line(sku);
        if (line == null) {
            throw new CartProblem(HttpStatus.NOT_FOUND, CartProblem.LINE_NOT_FOUND, "That SKU is not in the cart");
        }
        cart.removeItem(line);
        cart.advance(Instant.now(clock));
        carts.saveAndFlush(cart);
        return StoredCart.from(cart);
    }

    @Transactional
    public StoredCart clear(Owner owner, long expectedVersion) {
        Cart cart = carts.lockByOwner(owner.issuer(), owner.subject()).orElse(null);
        if (cart == null) {
            if (expectedVersion != 0) {
                throw stale();
            }
            return StoredCart.empty();
        }
        cart.getItems().size();
        if (cart.getAggregateVersion() != expectedVersion) {
            throw stale();
        }
        if (cart.getItems().isEmpty()) {
            return StoredCart.from(cart);
        }
        cart.clearItems();
        cart.advance(Instant.now(clock));
        carts.saveAndFlush(cart);
        return StoredCart.from(cart);
    }

    private Cart lockOrCreate(Owner owner, long expectedVersion) {
        Cart existing = carts.lockByOwner(owner.issuer(), owner.subject()).orElse(null);
        if (existing == null) {
            if (expectedVersion != 0) {
                throw stale();
            }
            return Cart.create(owner.issuer(), owner.subject(), Instant.now(clock));
        }
        existing.getItems().size();
        if (existing.getAggregateVersion() != expectedVersion) {
            throw stale();
        }
        return existing;
    }

    private Cart requireLocked(Owner owner, long expectedVersion) {
        Cart cart = carts.lockByOwner(owner.issuer(), owner.subject()).orElse(null);
        if (cart == null) {
            throw new CartProblem(HttpStatus.NOT_FOUND, CartProblem.LINE_NOT_FOUND, "That SKU is not in the cart");
        }
        cart.getItems().size();
        if (cart.getAggregateVersion() != expectedVersion) {
            throw stale();
        }
        return cart;
    }

    private static CartProblem stale() {
        return new CartProblem(
                HttpStatus.CONFLICT,
                CartProblem.STALE_VERSION,
                "The cart changed since it was loaded; reload and review"
        );
    }
}
