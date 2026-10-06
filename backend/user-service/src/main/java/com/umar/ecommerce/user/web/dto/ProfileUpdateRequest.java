package com.umar.ecommerce.user.web.dto;

import jakarta.validation.constraints.Size;

import java.util.Map;

public record ProfileUpdateRequest(
        @Size(max = 255) String displayName,
        Map<String, Object> preferences
) {
}
