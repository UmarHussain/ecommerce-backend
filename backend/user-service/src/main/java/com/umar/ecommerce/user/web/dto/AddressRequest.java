package com.umar.ecommerce.user.web.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record AddressRequest(
        @NotBlank @Size(max = 80) String label,
        @NotBlank @Size(max = 255) String line1,
        @Size(max = 255) String line2,
        @NotBlank @Size(max = 120) String city,
        @Size(max = 120) String region,
        @NotBlank @Size(max = 32) String postalCode,
        @NotBlank @Pattern(regexp = "[A-Za-z]{2}") String countryCode
) {
}
