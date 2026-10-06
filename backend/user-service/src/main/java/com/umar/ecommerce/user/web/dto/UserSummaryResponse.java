package com.umar.ecommerce.user.web.dto;

import java.util.Set;
import java.util.UUID;

public record UserSummaryResponse(
        UUID id,
        String subject,
        String email,
        String displayName,
        boolean enabled,
        boolean staff,
        String onboardingStatus,
        Set<String> directRoles
) {
}
