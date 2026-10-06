package com.umar.ecommerce.user.web.dto;

import jakarta.validation.constraints.Size;

import java.util.Set;

public record OnboardStaffRequest(
        Set<String> roles,
        @Size(max = 80) String employeeReference,
        @Size(max = 120) String department
) {
}
