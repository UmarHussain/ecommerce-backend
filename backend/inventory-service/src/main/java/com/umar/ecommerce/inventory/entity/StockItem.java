package com.umar.ecommerce.inventory.entity;

import com.umar.ecommerce.inventory.exception.InventoryProblem;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import org.springframework.http.HttpStatus;

import java.time.Instant;
import java.util.UUID;

/**
 * One stock pool per catalog variant. SKU and catalog variant id are assigned
 * at setup and are not updated. Reserved is not client-writable.
 */
@Entity
@Table(name = "stock_item")
public class StockItem {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "catalog_variant_id", nullable = false, updatable = false)
    private UUID catalogVariantId;

    @Column(name = "sku", nullable = false, updatable = false, length = 64)
    private String sku;

    @Column(name = "on_hand", nullable = false)
    private int onHand;

    @Column(name = "reserved", nullable = false)
    private int reserved;

    @Column(name = "product_name_snapshot", length = 200)
    private String productNameSnapshot;

    @Column(name = "variant_name_snapshot", length = 200)
    private String variantNameSnapshot;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Version
    @Column(name = "version", nullable = false)
    private long version;

    protected StockItem() {
    }

    public static StockItem open(
            UUID catalogVariantId,
            String sku,
            String productNameSnapshot,
            String variantNameSnapshot,
            int initialOnHand
    ) {
        if (catalogVariantId == null || sku == null || sku.isBlank()) {
            throw new InventoryProblem(
                    HttpStatus.BAD_REQUEST,
                    InventoryProblem.VALIDATION_FAILED,
                    "Catalog variant identity is required"
            );
        }
        if (initialOnHand < 0) {
            throw new InventoryProblem(
                    HttpStatus.BAD_REQUEST,
                    InventoryProblem.VALIDATION_FAILED,
                    "Initial on-hand must be zero or greater"
            );
        }
        StockItem item = new StockItem();
        item.catalogVariantId = catalogVariantId;
        item.sku = sku;
        item.productNameSnapshot = blankToNull(productNameSnapshot);
        item.variantNameSnapshot = blankToNull(variantNameSnapshot);
        item.onHand = initialOnHand;
        item.reserved = 0;
        return item;
    }

    /**
     * Writes a quantity the service has already checked for overflow and the
     * reserved ceiling. The database CHECK constraints remain the last guard.
     */
    public void commitOnHand(int nextOnHand) {
        if (nextOnHand < 0 || reserved > nextOnHand) {
            throw new InventoryProblem(
                    HttpStatus.CONFLICT,
                    InventoryProblem.STOCK_INVARIANT,
                    "On-hand cannot be negative or less than reserved"
            );
        }
        this.onHand = nextOnHand;
    }

    /**
     * Increases reserved when available stock covers the quantity.
     * Available is on-hand minus reserved.
     */
    public void reserve(int quantity) {
        requirePositive(quantity);
        int nextReserved = add(reserved, quantity);
        if (nextReserved > onHand) {
            throw new InventoryProblem(
                    HttpStatus.CONFLICT,
                    InventoryProblem.STOCK_INVARIANT,
                    "Available stock is not sufficient"
            );
        }
        this.reserved = nextReserved;
    }

    /** Decreases reserved only. On-hand stays where it is. */
    public void releaseReserved(int quantity) {
        requirePositive(quantity);
        if (quantity > reserved) {
            throw new InventoryProblem(
                    HttpStatus.CONFLICT,
                    InventoryProblem.STOCK_INVARIANT,
                    "Reserved stock is not sufficient to release"
            );
        }
        this.reserved = reserved - quantity;
    }

    /** Removes units from both on-hand and reserved after a held checkout. */
    public void consumeReserved(int quantity) {
        requirePositive(quantity);
        if (quantity > reserved || quantity > onHand) {
            throw new InventoryProblem(
                    HttpStatus.CONFLICT,
                    InventoryProblem.STOCK_INVARIANT,
                    "Reserved stock is not sufficient to consume"
            );
        }
        this.reserved = reserved - quantity;
        this.onHand = onHand - quantity;
    }

    /** Puts consumed units back on hand. Reserved is unchanged. */
    public void restock(int quantity) {
        requirePositive(quantity);
        this.onHand = add(onHand, quantity);
    }

    @PrePersist
    void onCreate() {
        Instant now = Instant.now();
        createdAt = now;
        updatedAt = now;
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = Instant.now();
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

    public int getOnHand() {
        return onHand;
    }

    public int getReserved() {
        return reserved;
    }

    public String getProductNameSnapshot() {
        return productNameSnapshot;
    }

    public String getVariantNameSnapshot() {
        return variantNameSnapshot;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public long getVersion() {
        return version;
    }

    public int available() {
        return onHand - reserved;
    }

    private static String blankToNull(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }

    private static void requirePositive(int quantity) {
        if (quantity <= 0) {
            throw new InventoryProblem(
                    HttpStatus.BAD_REQUEST,
                    InventoryProblem.VALIDATION_FAILED,
                    "Quantity must be greater than zero"
            );
        }
    }

    private static int add(int current, int quantity) {
        try {
            return Math.addExact(current, quantity);
        } catch (ArithmeticException exception) {
            throw new InventoryProblem(
                    HttpStatus.BAD_REQUEST,
                    InventoryProblem.QUANTITY_OVERFLOW,
                    "The quantity overflows the stock range"
            );
        }
    }
}
