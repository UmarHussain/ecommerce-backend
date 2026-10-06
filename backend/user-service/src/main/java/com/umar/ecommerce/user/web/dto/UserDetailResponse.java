package com.umar.ecommerce.user.web.dto;

import java.util.Set;

public record UserDetailResponse(
        UserSummaryResponse user,
        Set<String> directRoles,
        Set<String> effectiveRoles,
        StaffProfileResponse staffProfile
) {
}
