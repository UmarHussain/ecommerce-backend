package com.umar.ecommerce.inventory.dto.request;

import com.umar.ecommerce.inventory.domain.ReasonCode;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.UUID;

public record SetupStockRequest(
        @NotNull UUID catalogVariantId,
        @NotNull @Min(0) @Max(1_000_000_000) Integer initialOnHand,
        @NotNull ReasonCode reasonCode,
        @Size(max = 500) String note,
        @Size(max = 120) String reference
) {
}
