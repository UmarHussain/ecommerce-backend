package com.umar.ecommerce.inventory.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Stock row and the history entry written by this command")
public record StockCommandResponse(
        StockItemResponse stockItem,
        StockAdjustmentResponse adjustment
) {
}
