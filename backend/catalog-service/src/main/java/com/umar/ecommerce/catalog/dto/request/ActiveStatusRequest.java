package com.umar.ecommerce.catalog.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;

@Schema(description = "Activation state update")
public record ActiveStatusRequest(
        @NotNull(message = "active must not be null")
        @Schema(example = "true")
        Boolean active
) {
}
