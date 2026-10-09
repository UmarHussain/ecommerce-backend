package com.umar.ecommerce.order.dto.response;

import com.umar.ecommerce.order.domain.CleanupStatus;
import com.umar.ecommerce.order.domain.FulfilmentStatus;
import com.umar.ecommerce.order.domain.OrderStatus;
import com.umar.ecommerce.order.domain.PaymentStatus;
import com.umar.ecommerce.order.domain.SagaStep;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record OrderResponse(
        UUID id,
        OrderStatus orderStatus,
        PaymentStatus paymentStatus,
        FulfilmentStatus fulfilmentStatus,
        SagaStep sagaStep,
        boolean cancellationRequested,
        CleanupStatus cleanupStatus,
        String obligation,
        String currency,
        BigDecimal merchandiseTotal,
        BigDecimal shippingTotal,
        BigDecimal taxTotal,
        BigDecimal grandTotal,
        String shippingPolicy,
        String taxPolicy,
        boolean paymentSimulated,
        UUID quoteId,
        long cartVersion,
        Instant createdAt,
        Instant updatedAt,
        AddressSnapshotResponse address,
        List<OrderLineResponse> lines
) {
}
