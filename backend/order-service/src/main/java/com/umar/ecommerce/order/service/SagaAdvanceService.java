package com.umar.ecommerce.order.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.umar.ecommerce.order.domain.SagaPolicy;
import com.umar.ecommerce.order.entity.CustomerOrder;
import com.umar.ecommerce.order.entity.OrderHistory;
import com.umar.ecommerce.order.entity.OrderLine;
import com.umar.ecommerce.order.exception.OrderProblem;
import com.umar.ecommerce.order.messaging.CheckoutEvents;
import com.umar.ecommerce.order.messaging.CheckoutTopics;
import com.umar.ecommerce.order.messaging.OutboxWriter;
import com.umar.ecommerce.order.repository.OrderHistoryRepository;
import com.umar.ecommerce.order.repository.OrderRepository;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

@Service
public class SagaAdvanceService {

    private final OrderRepository orders;
    private final OrderHistoryRepository history;
    private final OutboxWriter outbox;
    private final ObjectMapper objectMapper;
    private final Clock clock;
    private final Duration commandTimeout;

    public SagaAdvanceService(
            OrderRepository orders,
            OrderHistoryRepository history,
            OutboxWriter outbox,
            ObjectMapper objectMapper,
            Clock clock,
            @Qualifier("commandTimeout") Duration commandTimeout
    ) {
        this.orders = orders;
        this.history = history;
        this.outbox = outbox;
        this.objectMapper = objectMapper;
        this.clock = clock;
        this.commandTimeout = commandTimeout;
    }

    @Transactional
    public void apply(UUID orderId, SagaPolicy.SignalType type, UUID commandId, UUID causationId) {
        CustomerOrder order = orders.lockById(orderId).orElse(null);
        if (order == null || !order.accepts(type, commandId)) {
            return;
        }
        SagaPolicy.Decision decision = SagaPolicy.apply(order.state(), new SagaPolicy.Signal(type));
        if (decision.ignored()) {
            return;
        }
        persist(order, decision, causationId);
    }

    @Transactional
    public CustomerOrder cancel(String issuer, String subject, UUID orderId) {
        CustomerOrder order = orders.lockById(orderId).orElseThrow(this::missing);
        if (!order.getOwnerIssuer().equals(issuer) || !order.getOwnerSubject().equals(subject)) {
            throw missing();
        }
        SagaPolicy.Decision decision = SagaPolicy.apply(order.state(), new SagaPolicy.Signal(SagaPolicy.SignalType.CANCEL_REQUESTED));
        if (decision.ignored() && "not-cancellable".equals(decision.detail())) {
            throw new OrderProblem(HttpStatus.CONFLICT, OrderProblem.NOT_CANCELLABLE, "This order cannot be cancelled");
        }
        if (!decision.ignored()) {
            persist(order, decision, null);
        }
        return order;
    }

    @Transactional
    public void deadline(UUID orderId) {
        CustomerOrder order = orders.lockById(orderId).orElse(null);
        if (order == null || order.getDeadlineAt() == null || order.getDeadlineAt().isAfter(Instant.now(clock))) {
            return;
        }
        SagaPolicy.Decision decision = SagaPolicy.apply(order.state(), new SagaPolicy.Signal(SagaPolicy.SignalType.DEADLINE));
        if (!decision.ignored()) {
            persist(order, decision, null);
        }
    }

    private void persist(CustomerOrder order, SagaPolicy.Decision decision, UUID causationId) {
        Instant now = Instant.now(clock);
        Instant deadline = decision.action() == SagaPolicy.Action.NONE ? null : now.plus(commandTimeout);
        order.apply(decision.state(), now, deadline);
        history.save(OrderHistory.record(order, decision.detail(), now));
        if (decision.action() != SagaPolicy.Action.NONE) {
            UUID commandId = order.commandId(decision.action(), UUID.randomUUID());
            outbox.stage(
                    order.getId(),
                    order.getVersion() + 1,
                    eventType(decision.action()),
                    commandId,
                    causationId,
                    topic(decision.action()),
                    payload(order, decision.action(), commandId),
                    now
            );
        }
    }

    private ObjectNode payload(CustomerOrder order, SagaPolicy.Action action, UUID commandId) {
        ObjectNode payload = objectMapper.createObjectNode();
        payload.put("orderId", order.getId().toString());
        switch (action) {
            case RESERVE -> {
                ArrayNode lines = payload.putArray("lines");
                for (OrderLine line : order.getLines()) {
                    ObjectNode node = lines.addObject();
                    node.put("catalogVariantId", line.getCatalogVariantId().toString());
                    node.put("sku", line.getSku());
                    node.put("quantity", line.getQuantity());
                }
            }
            case PAYMENT -> {
                payload.put("amount", order.getGrandTotal().toPlainString());
                payload.put("currency", order.getCurrency());
            }
            case QUERY_PAYMENT -> payload.put("paymentOperationId", commandId.toString());
            case REFUND -> {
                payload.put("paymentOperationId", order.getPaymentCommandId().toString());
                payload.put("amount", order.getGrandTotal().toPlainString());
                payload.put("currency", order.getCurrency());
            }
            case CLEANUP -> {
                payload.put("ownerIssuer", order.getOwnerIssuer());
                payload.put("ownerSubject", order.getOwnerSubject());
                payload.put("cartVersion", order.getCartVersion());
                ArrayNode lines = payload.putArray("lines");
                for (OrderLine line : order.getLines()) {
                    ObjectNode node = lines.addObject();
                    node.put("catalogVariantId", line.getCatalogVariantId().toString());
                    node.put("sku", line.getSku());
                    node.put("quantity", line.getQuantity());
                }
            }
            default -> {
            }
        }
        return payload;
    }

    private static String eventType(SagaPolicy.Action action) {
        return switch (action) {
            case RESERVE -> CheckoutEvents.RESERVE_STOCK;
            case HOLD -> CheckoutEvents.HOLD_RESERVATION;
            case PAYMENT -> CheckoutEvents.REQUEST_PAYMENT;
            case QUERY_PAYMENT -> CheckoutEvents.QUERY_PAYMENT;
            case CONSUME -> CheckoutEvents.CONSUME_RESERVATION;
            case RELEASE -> CheckoutEvents.RELEASE_RESERVATION;
            case REFUND -> CheckoutEvents.REFUND_PAYMENT;
            case RESTOCK -> CheckoutEvents.RESTOCK_RESERVATION;
            case CLEANUP -> CheckoutEvents.CLEAR_CART;
            case NONE -> throw new IllegalStateException("No event for an empty action");
        };
    }

    private static String topic(SagaPolicy.Action action) {
        return switch (action) {
            case RESERVE, HOLD, CONSUME, RELEASE, RESTOCK -> CheckoutTopics.INVENTORY_COMMANDS;
            case PAYMENT, QUERY_PAYMENT, REFUND -> CheckoutTopics.PAYMENT_COMMANDS;
            case CLEANUP -> CheckoutTopics.CART_COMMANDS;
            case NONE -> throw new IllegalStateException("No topic for an empty action");
        };
    }

    private OrderProblem missing() {
        return new OrderProblem(HttpStatus.NOT_FOUND, OrderProblem.NOT_FOUND, "Order was not found");
    }
}
