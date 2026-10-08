package com.umar.ecommerce.inventory.dto.request;

import com.umar.ecommerce.inventory.domain.ReasonCode;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record AdjustStockRequest(
        @NotNull @Min(Integer.MIN_VALUE) @Max(Integer.MAX_VALUE) Integer delta,
        @NotNull ReasonCode reasonCode,
        @NotNull @Min(0) Long expectedVersion,
        @Size(max = 500) String note,
        @Size(max = 120) String reference
) {
}
