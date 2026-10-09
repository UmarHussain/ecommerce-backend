package com.umar.ecommerce.cart.dto.request;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

public record SetQuantityRequest(
        @NotNull @Min(1) @Max(99) Integer quantity,
        @NotNull @Min(0) Long expectedVersion
) {
}
