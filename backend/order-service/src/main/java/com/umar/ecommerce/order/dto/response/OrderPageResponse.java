package com.umar.ecommerce.order.dto.response;

import java.util.List;

public record OrderPageResponse(
        List<OrderResponse> items,
        int page,
        int size,
        long total
) {
}
