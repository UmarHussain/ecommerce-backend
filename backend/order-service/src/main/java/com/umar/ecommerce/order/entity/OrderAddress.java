package com.umar.ecommerce.order.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.MapsId;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;

import java.util.UUID;

@Entity
@Table(name = "order_address")
public class OrderAddress {

    @Id
    @Column(name = "order_id")
    private UUID orderId;

    @MapsId
    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "order_id")
    private CustomerOrder order;

    @Column(name = "address_id", nullable = false, updatable = false)
    private UUID addressId;

    @Column(length = 80)
    private String label;

    @Column(nullable = false, length = 200)
    private String line1;

    @Column(length = 200)
    private String line2;

    @Column(nullable = false, length = 120)
    private String city;

    @Column(length = 120)
    private String region;

    @Column(name = "postal_code", nullable = false, length = 32)
    private String postalCode;

    @Column(name = "country_code", nullable = false, length = 2)
    private String countryCode;

    protected OrderAddress() {
    }

    public OrderAddress(
            UUID addressId,
            String label,
            String line1,
            String line2,
            String city,
            String region,
            String postalCode,
            String countryCode
    ) {
        this.addressId = addressId;
        this.label = label;
        this.line1 = line1;
        this.line2 = line2;
        this.city = city;
        this.region = region;
        this.postalCode = postalCode;
        this.countryCode = countryCode;
    }

    void assignOrder(CustomerOrder order) {
        this.order = order;
    }

    public UUID getAddressId() {
        return addressId;
    }

    public String getLabel() {
        return label;
    }

    public String getLine1() {
        return line1;
    }

    public String getLine2() {
        return line2;
    }

    public String getCity() {
        return city;
    }

    public String getRegion() {
        return region;
    }

    public String getPostalCode() {
        return postalCode;
    }

    public String getCountryCode() {
        return countryCode;
    }
}
