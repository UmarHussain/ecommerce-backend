package com.umar.ecommerce.order.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.util.UUID;

@Entity
@Table(name = "quote_line")
public class QuoteLine {

    @Id
    @Column(nullable = false, updatable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "quote_id", nullable = false, updatable = false)
    private CustomerQuote quote;

    @Column(name = "catalog_variant_id", nullable = false, updatable = false)
    private UUID catalogVariantId;

    @Column(nullable = false, updatable = false, length = 64)
    private String sku;

    @Column(name = "display_name", nullable = false, length = 200)
    private String displayName;

    @Column(nullable = false)
    private int quantity;

    @Column(name = "unit_price", nullable = false, precision = 12, scale = 2)
    private BigDecimal unitPrice;

    @Column(name = "line_total", nullable = false, precision = 12, scale = 2)
    private BigDecimal lineTotal;

    protected QuoteLine() {
    }

    public QuoteLine(
            UUID id,
            UUID catalogVariantId,
            String sku,
            String displayName,
            int quantity,
            BigDecimal unitPrice,
            BigDecimal lineTotal
    ) {
        this.id = id;
        this.catalogVariantId = catalogVariantId;
        this.sku = sku;
        this.displayName = displayName;
        this.quantity = quantity;
        this.unitPrice = unitPrice;
        this.lineTotal = lineTotal;
    }

    void assignQuote(CustomerQuote quote) {
        this.quote = quote;
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

    public String getDisplayName() {
        return displayName;
    }

    public int getQuantity() {
        return quantity;
    }

    public BigDecimal getUnitPrice() {
        return unitPrice;
    }

    public BigDecimal getLineTotal() {
        return lineTotal;
    }
}
