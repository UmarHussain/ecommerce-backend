package com.umar.ecommerce.catalog.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

@Schema(description = "Product variant data used to create a variant. The SKU is assigned only on create.")
public record ProductVariantRequest(
        @NotBlank(message = "sku must not be blank")
        @Size(min = 3, max = 64, message = "sku must be between 3 and 64 characters")
        @Pattern(
                regexp = "^[A-Za-z0-9][A-Za-z0-9._-]{2,63}$",
                message = "sku must contain only letters, numbers, dots, underscores, and hyphens"
        )
        @Schema(example = "HEADPHONE-BLK")
        String sku,

        @NotBlank(message = "name must not be blank")
        @Size(max = 200, message = "name must be at most 200 characters")
        @Schema(example = "Black")
        String name,

        @NotNull(message = "price must not be null")
        @DecimalMin(value = "0.0000", message = "price must not be negative")
        @Digits(
                integer = 15,
                fraction = 4,
                message = "price must have at most 15 integer digits and 4 fractional digits"
        )
        @Schema(example = "99.9900")
        BigDecimal price,

        @NotBlank(message = "currency must not be blank")
        @Pattern(regexp = "^[A-Za-z]{3}$", message = "currency must be a three-letter code")
        @Schema(example = "USD")
        String currency,

        @Size(max = 2048, message = "imageUrl must be at most 2048 characters")
        @Schema(example = "https://cdn.example.com/products/headphone-black.jpg")
        String imageUrl
) {
}
