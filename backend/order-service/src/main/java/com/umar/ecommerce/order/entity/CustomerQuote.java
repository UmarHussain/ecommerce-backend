package com.umar.ecommerce.order.entity;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "customer_quote")
public class CustomerQuote {

    @Id
    @Column(nullable = false, updatable = false)
    private UUID id;

    @Column(name = "owner_issuer", nullable = false, updatable = false, length = 500)
    private String ownerIssuer;

    @Column(name = "owner_subject", nullable = false, updatable = false, length = 255)
    private String ownerSubject;

    @Column(name = "cart_version", nullable = false)
    private long cartVersion;

    @Column(nullable = false, length = 3)
    private String currency;

    @Column(name = "merchandise_total", nullable = false, precision = 12, scale = 2)
    private BigDecimal merchandiseTotal;

    @Column(name = "shipping_total", nullable = false, precision = 12, scale = 2)
    private BigDecimal shippingTotal;

    @Column(name = "tax_total", nullable = false, precision = 12, scale = 2)
    private BigDecimal taxTotal;

    @Column(name = "grand_total", nullable = false, precision = 12, scale = 2)
    private BigDecimal grandTotal;

    @Column(name = "address_id", nullable = false)
    private UUID addressId;

    @Column(name = "address_label", length = 80)
    private String addressLabel;

    @Column(name = "address_line1", nullable = false, length = 200)
    private String addressLine1;

    @Column(name = "address_line2", length = 200)
    private String addressLine2;

    @Column(name = "address_city", nullable = false, length = 120)
    private String addressCity;

    @Column(name = "address_region", length = 120)
    private String addressRegion;

    @Column(name = "address_postal_code", nullable = false, length = 32)
    private String addressPostalCode;

    @Column(name = "address_country_code", nullable = false, length = 2)
    private String addressCountryCode;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Column(name = "consumed_order_id")
    private UUID consumedOrderId;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @OneToMany(mappedBy = "quote", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("sku asc")
    private List<QuoteLine> lines = new ArrayList<>();

    protected CustomerQuote() {
    }

    public CustomerQuote(
            UUID id,
            String ownerIssuer,
            String ownerSubject,
            long cartVersion,
            String currency,
            BigDecimal merchandiseTotal,
            BigDecimal shippingTotal,
            BigDecimal taxTotal,
            BigDecimal grandTotal,
            UUID addressId,
            String addressLabel,
            String addressLine1,
            String addressLine2,
            String addressCity,
            String addressRegion,
            String addressPostalCode,
            String addressCountryCode,
            Instant expiresAt,
            Instant createdAt
    ) {
        this.id = id;
        this.ownerIssuer = ownerIssuer;
        this.ownerSubject = ownerSubject;
        this.cartVersion = cartVersion;
        this.currency = currency;
        this.merchandiseTotal = merchandiseTotal;
        this.shippingTotal = shippingTotal;
        this.taxTotal = taxTotal;
        this.grandTotal = grandTotal;
        this.addressId = addressId;
        this.addressLabel = addressLabel;
        this.addressLine1 = addressLine1;
        this.addressLine2 = addressLine2;
        this.addressCity = addressCity;
        this.addressRegion = addressRegion;
        this.addressPostalCode = addressPostalCode;
        this.addressCountryCode = addressCountryCode;
        this.expiresAt = expiresAt;
        this.createdAt = createdAt;
    }

    public void addLine(QuoteLine line) {
        lines.add(line);
        line.assignQuote(this);
    }

    public void consume(UUID orderId) {
        this.consumedOrderId = orderId;
    }

    public void assignTotals(BigDecimal merchandise) {
        this.merchandiseTotal = merchandise;
        this.grandTotal = merchandise;
    }

    public UUID getId() {
        return id;
    }

    public String getOwnerIssuer() {
        return ownerIssuer;
    }

    public String getOwnerSubject() {
        return ownerSubject;
    }

    public long getCartVersion() {
        return cartVersion;
    }

    public String getCurrency() {
        return currency;
    }

    public BigDecimal getMerchandiseTotal() {
        return merchandiseTotal;
    }

    public BigDecimal getShippingTotal() {
        return shippingTotal;
    }

    public BigDecimal getTaxTotal() {
        return taxTotal;
    }

    public BigDecimal getGrandTotal() {
        return grandTotal;
    }

    public UUID getAddressId() {
        return addressId;
    }

    public String getAddressLabel() {
        return addressLabel;
    }

    public String getAddressLine1() {
        return addressLine1;
    }

    public String getAddressLine2() {
        return addressLine2;
    }

    public String getAddressCity() {
        return addressCity;
    }

    public String getAddressRegion() {
        return addressRegion;
    }

    public String getAddressPostalCode() {
        return addressPostalCode;
    }

    public String getAddressCountryCode() {
        return addressCountryCode;
    }

    public Instant getExpiresAt() {
        return expiresAt;
    }

    public UUID getConsumedOrderId() {
        return consumedOrderId;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public List<QuoteLine> getLines() {
        return lines;
    }
}
