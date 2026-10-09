package com.umar.ecommerce.order.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.umar.ecommerce.order.domain.MoneyPolicy;
import com.umar.ecommerce.order.dto.response.OrderResponse;
import com.umar.ecommerce.order.entity.CheckoutRequest;
import com.umar.ecommerce.order.entity.CustomerOrder;
import com.umar.ecommerce.order.entity.CustomerQuote;
import com.umar.ecommerce.order.entity.OrderAddress;
import com.umar.ecommerce.order.entity.OrderHistory;
import com.umar.ecommerce.order.entity.OrderLine;
import com.umar.ecommerce.order.entity.QuoteLine;
import com.umar.ecommerce.order.exception.OrderProblem;
import com.umar.ecommerce.order.messaging.CheckoutEvents;
import com.umar.ecommerce.order.messaging.CheckoutTopics;
import com.umar.ecommerce.order.messaging.OutboxWriter;
import com.umar.ecommerce.order.repository.CheckoutRequestRepository;
import com.umar.ecommerce.order.repository.OrderHistoryRepository;
import com.umar.ecommerce.order.repository.OrderRepository;
import com.umar.ecommerce.order.repository.QuoteRepository;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

@Service
public class CheckoutTransactionService {

    private final QuoteRepository quotes;
    private final OrderRepository orders;
    private final CheckoutRequestRepository requests;
    private final OrderHistoryRepository history;
    private final OutboxWriter outbox;
    private final OrderMapper mapper;
    private final ObjectMapper objectMapper;
    private final Clock clock;
    private final Duration commandTimeout;

    public CheckoutTransactionService(
            QuoteRepository quotes,
            OrderRepository orders,
            CheckoutRequestRepository requests,
            OrderHistoryRepository history,
            OutboxWriter outbox,
            OrderMapper mapper,
            ObjectMapper objectMapper,
            Clock clock,
            @Qualifier("commandTimeout") Duration commandTimeout
    ) {
        this.quotes = quotes;
        this.orders = orders;
        this.requests = requests;
        this.history = history;
        this.outbox = outbox;
        this.mapper = mapper;
        this.objectMapper = objectMapper;
        this.clock = clock;
        this.commandTimeout = commandTimeout;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public CheckoutRequest insertClaim(String issuer, String subject, String key, String fingerprint) {
        return requests.saveAndFlush(CheckoutRequest.start(
                UUID.randomUUID(), issuer, subject, key, fingerprint, Instant.now(clock)));
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public CheckoutRequest lockClaim(String issuer, String subject, String key) {
        return requests.lockByOwnerAndKey(issuer, subject, key).orElse(null);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void abandon(UUID requestId) {
        CheckoutRequest request = requests.findById(requestId).orElse(null);
        if (request != null && CheckoutRequest.IN_PROGRESS.equals(request.getRequestStatus())) {
            requests.delete(request);
        }
    }

    @Transactional
    public CheckoutResult accept(UUID requestId, UUID quoteId, String issuer, String subject, long observedCartVersion) {
        Instant now = Instant.now(clock);
        CheckoutRequest request = requests.findById(requestId).orElseThrow();
        if (CheckoutRequest.COMPLETED.equals(request.getRequestStatus())) {
            String location = request.getOrderId() == null ? null : "/api/v1/orders/" + request.getOrderId();
            return new CheckoutResult(request.getHttpStatus(), request.getResponseBody(), location);
        }
        CustomerQuote quote = quotes.lockById(quoteId).orElseThrow(this::missingQuote);
        if (!quote.getOwnerIssuer().equals(issuer) || !quote.getOwnerSubject().equals(subject)) {
            throw missingQuote();
        }
        if (quote.getConsumedOrderId() != null) {
            return completeProblem(request, OrderProblem.review("QUOTE_CONSUMED", "This quote was already used"));
        }
        if (!quote.getExpiresAt().isAfter(now)) {
            throw OrderProblem.review("QUOTE_EXPIRED", "This quote has expired");
        }
        if (quote.getCartVersion() != observedCartVersion) {
            throw OrderProblem.review("CART_CHANGED", "The cart changed after the quote was reviewed");
        }
        UUID orderId = UUID.randomUUID();
        UUID reserveCommandId = UUID.randomUUID();
        CustomerOrder order = CustomerOrder.accept(orderId, quote, reserveCommandId, now, now.plus(commandTimeout));
        for (QuoteLine line : quote.getLines()) {
            order.addLine(new OrderLine(
                    UUID.randomUUID(),
                    line.getCatalogVariantId(),
                    line.getSku(),
                    line.getDisplayName(),
                    line.getQuantity(),
                    line.getUnitPrice(),
                    line.getLineTotal()
            ));
        }
        order.assignAddress(new OrderAddress(
                quote.getAddressId(),
                quote.getAddressLabel(),
                quote.getAddressLine1(),
                quote.getAddressLine2(),
                quote.getAddressCity(),
                quote.getAddressRegion(),
                quote.getAddressPostalCode(),
                quote.getAddressCountryCode()
        ));
        orders.saveAndFlush(order);
        quote.consume(orderId);
        history.save(OrderHistory.record(order, "checkout accepted", now));
        stageReserve(order, now);
        OrderResponse body = mapper.toOrder(order);
        String json;
        try {
            json = objectMapper.writeValueAsString(body);
        } catch (Exception exception) {
            throw new IllegalStateException("Could not encode the accepted order", exception);
        }
        request.complete(HttpStatus.ACCEPTED.value(), json, orderId, now);
        return new CheckoutResult(HttpStatus.ACCEPTED.value(), json, "/api/v1/orders/" + orderId);
    }

    private void stageReserve(CustomerOrder order, Instant now) {
        ObjectNode payload = objectMapper.createObjectNode();
        payload.put("orderId", order.getId().toString());
        ArrayNode lines = payload.putArray("lines");
        order.getLines().forEach(line -> {
            ObjectNode node = lines.addObject();
            node.put("catalogVariantId", line.getCatalogVariantId().toString());
            node.put("sku", line.getSku());
            node.put("quantity", line.getQuantity());
        });
        outbox.stage(
                order.getId(),
                1,
                CheckoutEvents.RESERVE_STOCK,
                order.getReserveCommandId(),
                null,
                CheckoutTopics.INVENTORY_COMMANDS,
                payload,
                now
        );
    }

    private CheckoutResult completeProblem(CheckoutRequest request, OrderProblem problem) {
        String body = """
                {"code":"%s","reason":"%s","detail":"%s"}
                """.formatted(problem.code(), problem.reason(), problem.getMessage());
        request.complete(problem.status().value(), body, null, Instant.now(clock));
        return new CheckoutResult(problem.status().value(), body, null);
    }

    private OrderProblem missingQuote() {
        return new OrderProblem(HttpStatus.NOT_FOUND, OrderProblem.NOT_FOUND, "Quote was not found");
    }

    public static String shippingPolicy() {
        return MoneyPolicy.SHIPPING_POLICY;
    }
}
