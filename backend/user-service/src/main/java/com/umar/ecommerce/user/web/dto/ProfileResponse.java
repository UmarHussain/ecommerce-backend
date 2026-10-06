package com.umar.ecommerce.user.web.dto;

import java.util.List;
import java.util.Map;
import java.util.UUID;

public record ProfileResponse(
        UUID id,
        String issuer,
        String subject,
        String email,
        String displayName,
        Map<String, Object> preferences,
        List<AddressResponse> addresses,
        StaffProfileResponse staffProfile,
        boolean profilePersistenceImplemented
) {
}
