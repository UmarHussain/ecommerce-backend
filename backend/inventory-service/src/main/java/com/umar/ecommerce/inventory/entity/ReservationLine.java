package com.umar.ecommerce.inventory.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import java.util.UUID;

@Entity
@Table(name = "reservation_line")
public class ReservationLine {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "reservation_id", nullable = false, updatable = false)
    private Reservation reservation;

    @Column(name = "catalog_variant_id", nullable = false, updatable = false)
    private UUID catalogVariantId;

    @Column(name = "sku", nullable = false, updatable = false, length = 64)
    private String sku;

    @Column(name = "quantity", nullable = false, updatable = false)
    private int quantity;

    protected ReservationLine() {
    }

    public static ReservationLine create(Reservation reservation, UUID catalogVariantId, String sku, int quantity) {
        ReservationLine line = new ReservationLine();
        line.id = UUID.randomUUID();
        line.reservation = reservation;
        line.catalogVariantId = catalogVariantId;
        line.sku = sku;
        line.quantity = quantity;
        return line;
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
}
