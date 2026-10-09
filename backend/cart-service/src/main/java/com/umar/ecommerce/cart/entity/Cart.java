package com.umar.ecommerce.cart.entity;

import com.umar.ecommerce.cart.domain.CartLimits;
import com.umar.ecommerce.cart.exception.CartProblem;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import org.springframework.http.HttpStatus;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Customer cart aggregate. {@code aggregateVersion} is incremented in the
 * write transaction when the lines change. It is not a Hibernate {@code @Version}:
 * changing a child does not dirty this column by itself, so the writer calls
 * {@link #advance(Instant)} after it changes items.
 */
@Entity
@Table(name = "cart")
public class Cart {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "owner_issuer", nullable = false, updatable = false, length = 500)
    private String ownerIssuer;

    @Column(name = "owner_subject", nullable = false, updatable = false, length = 255)
    private String ownerSubject;

    @Column(name = "aggregate_version", nullable = false)
    private long aggregateVersion;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @OneToMany(mappedBy = "cart", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("sku asc")
    private List<CartItem> items = new ArrayList<>();

    protected Cart() {
    }

    public static Cart create(String issuer, String subject, Instant now) {
        Cart cart = new Cart();
        cart.ownerIssuer = issuer;
        cart.ownerSubject = subject;
        cart.aggregateVersion = 0;
        cart.createdAt = now;
        cart.updatedAt = now;
        return cart;
    }

    public CartItem line(String sku) {
        for (CartItem item : items) {
            if (item.getSku().equals(sku)) {
                return item;
            }
        }
        return null;
    }

    public void addItem(CartItem item) {
        if (items.size() >= CartLimits.MAX_ITEMS) {
            throw new CartProblem(
                    HttpStatus.CONFLICT,
                    CartProblem.ITEM_LIMIT,
                    "A cart can hold at most 100 different items"
            );
        }
        items.add(item);
    }

    public void removeItem(CartItem item) {
        items.remove(item);
    }

    public void clearItems() {
        items.clear();
    }

    public void advance(Instant now) {
        aggregateVersion = aggregateVersion + 1;
        updatedAt = now;
    }

    public UUID getId() {
        return id;
    }

    public long getAggregateVersion() {
        return aggregateVersion;
    }

    public List<CartItem> getItems() {
        return items;
    }
}
