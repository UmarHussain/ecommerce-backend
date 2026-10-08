package com.umar.ecommerce.catalog.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

@Schema(description = "Category update. expectedVersion is required and must match the stored version.")
public record CategoryUpdateRequest(
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
        String slug,

        @NotNull(message = "expectedVersion must not be null")
        @Min(value = 0, message = "expectedVersion must be zero or greater")
        @Schema(description = "Version loaded by the caller. A stale value is rejected with 409.")
        Long expectedVersion
) {
}
