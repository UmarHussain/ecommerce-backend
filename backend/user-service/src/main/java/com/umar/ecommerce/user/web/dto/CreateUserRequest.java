package com.umar.ecommerce.user.web.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.util.Set;

public record CreateUserRequest(
        @NotBlank @Email @Size(max = 320) String email,
        @Size(max = 120) String firstName,
        @Size(max = 120) String lastName,
        @Size(min = 8, max = 128) String temporaryPassword,
        Set<String> roles,
        boolean onboardAsStaff,
        @Size(max = 80) String employeeReference,
        @Size(max = 120) String department
) {
}
