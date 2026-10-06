package com.umar.ecommerce.catalog.dto.request;

import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.util.List;

@Schema(description = "Ordered SKU batch lookup request")
public record BatchVariantRequest(
        @NotNull(message = "skus must not be null")
        @Size(min = 1, max = 100, message = "skus must contain between 1 and 100 entries")
        @ArraySchema(schema = @Schema(example = "HEADPHONE-BLK"))
        List<
                @Valid
                @NotBlank(message = "sku must not be blank")
                @Size(min = 3, max = 64, message = "sku must be between 3 and 64 characters")
                @Pattern(
                        regexp = "^[A-Za-z0-9][A-Za-z0-9._-]{2,63}$",
                        message = "sku must contain only letters, numbers, dots, underscores, and hyphens"
                )
                String
        > skus
) {
}
