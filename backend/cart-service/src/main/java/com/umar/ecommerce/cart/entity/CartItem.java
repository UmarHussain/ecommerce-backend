package com.umar.ecommerce.cart.entity;

import com.umar.ecommerce.cart.catalog.CatalogVariant;
import com.umar.ecommerce.cart.domain.CartLimits;
import com.umar.ecommerce.cart.exception.CartProblem;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import org.springframework.http.HttpStatus;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "cart_item")
public class CartItem {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "cart_id", nullable = false, updatable = false)
    private Cart cart;

    @Column(name = "catalog_variant_id", nullable = false, updatable = false)
    private UUID catalogVariantId;

    @Column(name = "sku", nullable = false, updatable = false, length = 64)
    private String sku;

    @Column(name = "quantity", nullable = false)
    private int quantity;

    @Column(name = "display_name", nullable = false, length = 200)
    private String displayName;

    @Column(name = "image_url", length = 2048)
    private String imageUrl;

    @Column(name = "unit_price", nullable = false, precision = 19, scale = 4)
    private BigDecimal unitPrice;

    @Column(name = "currency", nullable = false, length = 3)
    private String currency;

    @Column(name = "snapshot_at", nullable = false)
    private Instant snapshotAt;

    protected CartItem() {
    }

    public static CartItem create(Cart cart, CatalogVariant variant, int quantity, Instant snapshotAt) {
        if (quantity < CartLimits.MIN_QUANTITY || quantity > CartLimits.MAX_QUANTITY) {
            throw new CartProblem(HttpStatus.BAD_REQUEST, CartProblem.VALIDATION_FAILED, "quantity must be from 1 to 99");
        }
        CartItem item = new CartItem();
        item.cart = cart;
        item.catalogVariantId = variant.id();
        item.sku = variant.sku();
        item.quantity = quantity;
        item.displayName = requireName(variant.name());
        item.imageUrl = variant.imageUrl();
        item.unitPrice = requirePrice(variant.price());
        item.currency = requireCurrency(variant.currency());
        item.snapshotAt = snapshotAt;
        return item;
    }

    public void setQuantity(int quantity) {
        if (quantity < CartLimits.MIN_QUANTITY || quantity > CartLimits.MAX_QUANTITY) {
            throw new CartProblem(HttpStatus.BAD_REQUEST, CartProblem.VALIDATION_FAILED, "quantity must be from 1 to 99");
        }
        this.quantity = quantity;
    }

    public void replaceSnapshot(CatalogVariant variant, Instant snapshotAt) {
        if (!variant.sku().equals(sku) || !variant.id().equals(catalogVariantId)) {
            throw new CartProblem(
                    HttpStatus.CONFLICT,
                    CartProblem.SKU_UNAVAILABLE,
                    "Catalog identity does not match the stored line"
            );
        }
        this.displayName = requireName(variant.name());
        this.imageUrl = variant.imageUrl();
        this.unitPrice = requirePrice(variant.price());
        this.currency = requireCurrency(variant.currency());
        this.snapshotAt = snapshotAt;
    }

    public BigDecimal lineTotal() {
        return unitPrice.multiply(BigDecimal.valueOf(quantity)).setScale(4, RoundingMode.HALF_UP);
    }

    public UUID getId() {
        return id;
    }

    public UUID getCatalogVariantId() {
        return catalogVariantId;
    }

    public String getSku() {
        return sku;
    }

    public int getQuantity() {
        return quantity;
    }

    public String getDisplayName() {
        return displayName;
    }

    public String getImageUrl() {
        return imageUrl;
    }

    public BigDecimal getUnitPrice() {
        return unitPrice;
    }

    public String getCurrency() {
        return currency;
    }

    public Instant getSnapshotAt() {
        return snapshotAt;
    }

    private static String requireName(String name) {
        if (name == null || name.isBlank() || name.length() > 200) {
            throw new CartProblem(HttpStatus.CONFLICT, CartProblem.SKU_UNAVAILABLE, "Catalog did not return a usable variant name");
        }
        return name;
    }

    private static BigDecimal requirePrice(BigDecimal price) {
        if (price == null || price.signum() < 0 || price.precision() > 19 || price.scale() > 4) {
            throw new CartProblem(HttpStatus.CONFLICT, CartProblem.SKU_UNAVAILABLE, "Catalog did not return a usable price");
        }
        return price;
    }

    private static String requireCurrency(String currency) {
        if (currency == null || currency.length() != 3) {
            throw new CartProblem(HttpStatus.CONFLICT, CartProblem.SKU_UNAVAILABLE, "Catalog did not return a usable currency");
        }
        return currency;
    }
}
