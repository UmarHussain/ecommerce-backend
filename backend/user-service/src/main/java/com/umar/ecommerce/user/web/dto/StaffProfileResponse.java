package com.umar.ecommerce.user.web.dto;

public record StaffProfileResponse(
        String employeeReference,
        String department,
        String onboardingStatus
) {
}
