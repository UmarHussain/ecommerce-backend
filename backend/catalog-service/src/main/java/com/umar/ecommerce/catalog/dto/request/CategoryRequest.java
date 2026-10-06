package com.umar.ecommerce.catalog.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

@Schema(description = "Category data used for create and update operations")
public record CategoryRequest(
        @NotBlank(message = "name must not be blank")
        @Size(max = 160, message = "name must be at most 160 characters")
        @Schema(example = "Electronics")
        String name,

        @NotBlank(message = "slug must not be blank")
        @Size(max = 120, message = "slug must be at most 120 characters")
        @Pattern(
                regexp = "^[A-Za-z0-9]+(?:-[A-Za-z0-9]+)*$",
                message = "slug must contain only letters, numbers, and single hyphens"
        )
        @Schema(example = "electronics")
        String slug
) {
}
