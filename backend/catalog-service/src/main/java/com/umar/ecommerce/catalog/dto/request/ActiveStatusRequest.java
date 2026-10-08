package com.umar.ecommerce.catalog.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

@Schema(description = "Activation state update. expectedVersion is required.")
public record ActiveStatusRequest(
        @NotNull(message = "active must not be null")
        @Schema(example = "true")
        Boolean active,

        @NotNull(message = "expectedVersion must not be null")
        @Min(value = 0, message = "expectedVersion must be zero or greater")
        @Schema(description = "Version loaded by the caller. A stale value is rejected with 409.")
        Long expectedVersion
) {
}
