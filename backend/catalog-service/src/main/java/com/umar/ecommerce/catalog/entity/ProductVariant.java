package com.umar.ecommerce.catalog.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.util.Locale;
import java.util.Objects;

@Entity
@Table(name = "product_variant")
public class ProductVariant extends AuditableEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "product_id", nullable = false)
    private Product product;

    @Column(name = "sku", nullable = false, unique = true, length = 64)
    private String sku;

    @Column(name = "name", nullable = false, length = 200)
    private String name;

    @Column(name = "price", nullable = false, precision = 19, scale = 4)
    private BigDecimal price;

    @Column(name = "currency", nullable = false, length = 3)
    private String currency;

    @Column(name = "image_url", length = 2048)
    private String imageUrl;

    protected ProductVariant() {
    }

    public ProductVariant(
            Product product,
            String sku,
            String name,
            BigDecimal price,
            String currency,
            String imageUrl
    ) {
        changeProduct(product);
        changeSku(sku);
        updateDetails(name, price, currency, imageUrl);
    }

    public void updateDetails(String name, BigDecimal price, String currency, String imageUrl) {
        this.name = requireText(name, "name");
        this.price = requireNonNegativePrice(price);
        this.currency = normalizeCurrency(currency);
        this.imageUrl = normalizeNullableText(imageUrl);
    }

    public void changeSku(String sku) {
        this.sku = requireText(sku, "sku").toUpperCase(Locale.ROOT);
    }

    public void changeProduct(Product product) {
        this.product = Objects.requireNonNull(product, "product must not be null");
    }

    public Product getProduct() {
        return product;
    }

    public String getSku() {
        return sku;
    }

    public String getName() {
        return name;
    }

    public BigDecimal getPrice() {
        return price;
    }

    public String getCurrency() {
        return currency;
    }

    public String getImageUrl() {
        return imageUrl;
    }

    private static BigDecimal requireNonNegativePrice(BigDecimal price) {
        Objects.requireNonNull(price, "price must not be null");
        if (price.signum() < 0) {
            throw new IllegalArgumentException("price must not be negative");
        }
        return price;
    }

    private static String normalizeCurrency(String currency) {
        return requireText(currency, "currency").toUpperCase(Locale.ROOT);
    }

    private static String normalizeNullableText(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }

    private static String requireText(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(fieldName + " must not be blank");
        }
        return value.trim();
    }
}
