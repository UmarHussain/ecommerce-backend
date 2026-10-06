package com.umar.ecommerce.user.web.dto;

import com.umar.ecommerce.user.entity.CustomerAddress;

import java.util.UUID;

public record AddressResponse(
        UUID id,
        String label,
        String line1,
        String line2,
        String city,
        String region,
        String postalCode,
        String countryCode
) {
    public static AddressResponse from(CustomerAddress address) {
        return new AddressResponse(
                address.getId(),
                address.getLabel(),
                address.getLine1(),
                address.getLine2(),
                address.getCity(),
                address.getRegion(),
                address.getPostalCode(),
                address.getCountryCode()
        );
    }
}
