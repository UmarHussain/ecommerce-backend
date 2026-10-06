package com.umar.ecommerce.user.web.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.util.Set;

public record CreateRoleBundleRequest(
        @NotBlank @Size(max = 80) @Pattern(regexp = "[A-Za-z][A-Za-z0-9_\\-]{1,79}") String name,
        @Size(max = 500) String description,
        @NotEmpty Set<String> permissions
) {
}
