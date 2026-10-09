package com.umar.ecommerce.order.dto.response;

import java.util.UUID;

public record AddressSnapshotResponse(
        UUID addressId,
        String label,
        String line1,
        String line2,
        String city,
        String region,
        String postalCode,
        String countryCode
) {
}
