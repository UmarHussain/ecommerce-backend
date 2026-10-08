package com.umar.ecommerce.catalog.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.util.UUID;

@Schema(description = "Product data used to create a product. Activation is not accepted on create.")
public record ProductRequest(
        @NotBlank(message = "name must not be blank")
        @Size(max = 200, message = "name must be at most 200 characters")
        @Schema(example = "Wireless Headphones")
        String name,

        @NotBlank(message = "slug must not be blank")
        @Size(max = 160, message = "slug must be at most 160 characters")
        @Pattern(
                regexp = "^[A-Za-z0-9]+(?:-[A-Za-z0-9]+)*$",
                message = "slug must contain only letters, numbers, and single hyphens"
        )
        @Schema(example = "wireless-headphones")
        String slug,

        @Schema(example = "Noise-cancelling over-ear headphones")
        String description,

        @NotNull(message = "categoryId must not be null")
        @Schema(description = "Owning category identifier")
        UUID categoryId
) {
}
