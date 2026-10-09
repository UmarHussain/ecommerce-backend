package com.umar.ecommerce.order.service;

import com.umar.ecommerce.order.domain.MoneyPolicy;
import com.umar.ecommerce.order.dto.response.OrderPageResponse;
import com.umar.ecommerce.order.dto.response.OrderResponse;
import com.umar.ecommerce.order.dto.response.QuoteResponse;
import com.umar.ecommerce.order.entity.CheckoutRequest;
import com.umar.ecommerce.order.entity.CustomerOrder;
import com.umar.ecommerce.order.entity.CustomerQuote;
import com.umar.ecommerce.order.entity.QuoteLine;
import com.umar.ecommerce.order.exception.OrderProblem;
import com.umar.ecommerce.order.remote.CheckoutRemote;
import com.umar.ecommerce.order.remote.CheckoutSnapshot;
import com.umar.ecommerce.order.repository.CheckoutRequestRepository;
import com.umar.ecommerce.order.repository.OrderRepository;
import com.umar.ecommerce.order.repository.QuoteRepository;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Service
public class QuoteCheckoutService {

    private static final Pattern IDEMPOTENCY_KEY = Pattern.compile("^[A-Za-z0-9._:-]{8,128}$");

    private final CheckoutRemote remote;
    private final QuoteRepository quotes;
    private final OrderRepository orders;
    private final CheckoutRequestRepository requests;
    private final CheckoutTransactionService transactions;
    private final SagaAdvanceService saga;
    private final OrderMapper mapper;
    private final Clock clock;
    private final Duration quoteTtl;

    public QuoteCheckoutService(
            CheckoutRemote remote,
            QuoteRepository quotes,
            OrderRepository orders,
            CheckoutRequestRepository requests,
            CheckoutTransactionService transactions,
            SagaAdvanceService saga,
            OrderMapper mapper,
            Clock clock,
            @Qualifier("quoteTtl") Duration quoteTtl
    ) {
        this.remote = remote;
        this.quotes = quotes;
        this.orders = orders;
        this.requests = requests;
        this.transactions = transactions;
        this.saga = saga;
        this.mapper = mapper;
        this.clock = clock;
        this.quoteTtl = quoteTtl;
    }

    public QuoteResponse quote(String issuer, String subject, String bearer, long expectedCartVersion, UUID addressId, String correlationId) {
        CheckoutSnapshot.CartSnapshotView cart = remote.cart(bearer, correlationId);
        if (cart.version() != expectedCartVersion) {
            throw OrderProblem.review("CART_CHANGED", "The cart changed. Review it and request a new quote");
        }
        if (cart.lines().isEmpty()) {
            throw new OrderProblem(HttpStatus.UNPROCESSABLE_ENTITY, OrderProblem.VALIDATION_FAILED, "The cart is empty");
        }
        List<String> currencies = cart.lines().stream().map(CheckoutSnapshot.CartLine::currency).distinct().toList();
        if (currencies.size() != 1) {
            throw OrderProblem.review("MIXED_CURRENCY", "This demo does not convert or add different currencies");
        }
        if (cart.lines().stream().anyMatch(line -> !"CONFIRMED".equals(line.catalogState()))) {
            throw OrderProblem.review("UNAVAILABLE", "A cart line is not an active catalog variant");
        }
        List<CheckoutSnapshot.CatalogLine> catalog = remote.catalog(
                cart.lines().stream().map(CheckoutSnapshot.CartLine::sku).toList(),
                correlationId
        );
        Map<String, CheckoutSnapshot.CatalogLine> bySku = catalog.stream()
                .collect(Collectors.toMap(CheckoutSnapshot.CatalogLine::sku, Function.identity()));
        if (bySku.size() != cart.lines().size()) {
            throw OrderProblem.review("UNAVAILABLE", "A cart line is not an active catalog variant");
        }
        CheckoutSnapshot.OwnedAddress address = remote.address(bearer, addressId, correlationId);
        Instant now = Instant.now(clock);
        CustomerQuote quote = new CustomerQuote(
                UUID.randomUUID(),
                issuer,
                subject,
                cart.version(),
                currencies.get(0),
                BigDecimal.ZERO,
                MoneyPolicy.ZERO,
                MoneyPolicy.ZERO,
                BigDecimal.ZERO,
                address.id(),
                address.label(),
                address.line1(),
                address.line2(),
                address.city(),
                address.region(),
                address.postalCode(),
                address.countryCode(),
                now.plus(quoteTtl),
                now
        );
        BigDecimal merchandise = MoneyPolicy.ZERO;
        for (CheckoutSnapshot.CartLine line : cart.lines()) {
            CheckoutSnapshot.CatalogLine authoritative = bySku.get(line.sku());
            if (!authoritative.id().equals(line.catalogVariantId()) || !authoritative.currency().equals(line.currency())) {
                throw OrderProblem.review("UNAVAILABLE", "A cart line no longer matches the catalog");
            }
            BigDecimal unit = MoneyPolicy.money(authoritative.price());
            BigDecimal total = MoneyPolicy.lineTotal(unit, line.quantity());
            merchandise = merchandise.add(total);
            quote.addLine(new QuoteLine(
                    UUID.randomUUID(),
                    authoritative.id(),
                    authoritative.sku(),
                    line.displayName() == null || line.displayName().isBlank() ? authoritative.name() : line.displayName(),
                    line.quantity(),
                    unit,
                    total
            ));
        }
        quote.assignTotals(merchandise);
        quotes.save(quote);
        return mapper.toQuote(quote);
    }

    public CheckoutResult accept(String issuer, String subject, String bearer, UUID quoteId, String idempotencyKey, String correlationId) {
        if (idempotencyKey == null || !IDEMPOTENCY_KEY.matcher(idempotencyKey).matches()) {
            throw new OrderProblem(HttpStatus.BAD_REQUEST, OrderProblem.VALIDATION_FAILED, "Idempotency-Key must be 8 to 128 characters");
        }
        String fingerprint = fingerprint(quoteId);
        CheckoutRequest existing = requests.findByOwnerIssuerAndOwnerSubjectAndIdempotencyKey(issuer, subject, idempotencyKey).orElse(null);
        if (existing != null && CheckoutRequest.COMPLETED.equals(existing.getRequestStatus())) {
            if (!existing.getRequestFingerprint().equals(fingerprint)) {
                throw new OrderProblem(HttpStatus.CONFLICT, OrderProblem.IDEMPOTENCY_CONFLICT, "This idempotency key was used for a different checkout");
            }
            String location = existing.getOrderId() == null ? null : "/api/v1/orders/" + existing.getOrderId();
            return new CheckoutResult(existing.getHttpStatus(), existing.getResponseBody(), location);
        }
        CustomerQuote quote = quotes.findById(quoteId).orElseThrow(this::missingQuote);
        if (!quote.getOwnerIssuer().equals(issuer) || !quote.getOwnerSubject().equals(subject)) {
            throw missingQuote();
        }
        if (quote.getConsumedOrderId() != null) {
            throw OrderProblem.review("QUOTE_CONSUMED", "This quote was already used");
        }
        if (!quote.getExpiresAt().isAfter(Instant.now(clock))) {
            throw OrderProblem.review("QUOTE_EXPIRED", "This quote has expired");
        }
        CheckoutSnapshot.CartSnapshotView cart = remote.cart(bearer, correlationId);
        if (cart.version() != quote.getCartVersion()) {
            throw OrderProblem.review("CART_CHANGED", "The cart changed. Request a new quote");
        }
        List<CheckoutSnapshot.CatalogLine> catalog = remote.catalog(
                quote.getLines().stream().map(QuoteLine::getSku).toList(),
                correlationId
        );
        Map<String, CheckoutSnapshot.CatalogLine> bySku = catalog.stream()
                .collect(Collectors.toMap(CheckoutSnapshot.CatalogLine::sku, Function.identity()));
        for (QuoteLine line : quote.getLines()) {
            CheckoutSnapshot.CatalogLine authoritative = bySku.get(line.getSku());
            if (authoritative == null
                    || !authoritative.id().equals(line.getCatalogVariantId())
                    || MoneyPolicy.money(authoritative.price()).compareTo(line.getUnitPrice()) != 0
                    || !authoritative.currency().equals(quote.getCurrency())) {
                throw OrderProblem.review("PRICE_CHANGED", "The catalog price changed. Request a new quote");
            }
        }
        remote.address(bearer, quote.getAddressId(), correlationId);
        CheckoutRequest claim = claim(issuer, subject, idempotencyKey, fingerprint);
        if (CheckoutRequest.COMPLETED.equals(claim.getRequestStatus())) {
            if (!claim.getRequestFingerprint().equals(fingerprint)) {
                throw new OrderProblem(HttpStatus.CONFLICT, OrderProblem.IDEMPOTENCY_CONFLICT, "This idempotency key was used for a different checkout");
            }
            String location = claim.getOrderId() == null ? null : "/api/v1/orders/" + claim.getOrderId();
            return new CheckoutResult(claim.getHttpStatus(), claim.getResponseBody(), location);
        }
        try {
            return transactions.accept(claim.getId(), quoteId, issuer, subject, cart.version());
        } catch (RuntimeException exception) {
            transactions.abandon(claim.getId());
            throw exception;
        }
    }

    @Transactional(readOnly = true)
    public OrderPageResponse list(String issuer, String subject, int page, int size) {
        int bounded = Math.min(Math.max(size, 1), 20);
        Page<CustomerOrder> result = orders.findByOwnerIssuerAndOwnerSubject(issuer, subject, PageRequest.of(Math.max(page, 0), bounded));
        return new OrderPageResponse(result.stream().map(mapper::toOrder).toList(), result.getNumber(), result.getSize(), result.getTotalElements());
    }

    public OrderResponse get(String issuer, String subject, UUID orderId) {
        return mapper.toOrder(orders.findOwned(issuer, subject, orderId).orElseThrow(this::missingOrder));
    }

    public OrderResponse cancel(String issuer, String subject, UUID orderId) {
        saga.cancel(issuer, subject, orderId);
        return get(issuer, subject, orderId);
    }

    private CheckoutRequest claim(String issuer, String subject, String key, String fingerprint) {
        CheckoutRequest existing = transactions.lockClaim(issuer, subject, key);
        if (existing != null) {
            return requireReusable(existing, fingerprint);
        }
        try {
            return transactions.insertClaim(issuer, subject, key, fingerprint);
        } catch (DataIntegrityViolationException exception) {
            CheckoutRequest raced = transactions.lockClaim(issuer, subject, key);
            if (raced == null) {
                throw exception;
            }
            return requireReusable(raced, fingerprint);
        }
    }

    private static CheckoutRequest requireReusable(CheckoutRequest existing, String fingerprint) {
        if (!existing.getRequestFingerprint().equals(fingerprint)) {
            throw new OrderProblem(HttpStatus.CONFLICT, OrderProblem.IDEMPOTENCY_CONFLICT, "This idempotency key was used for a different checkout");
        }
        if (CheckoutRequest.IN_PROGRESS.equals(existing.getRequestStatus())) {
            throw new OrderProblem(HttpStatus.CONFLICT, OrderProblem.IDEMPOTENCY_IN_PROGRESS, "This checkout is already in progress");
        }
        return existing;
    }

    private static String fingerprint(UUID quoteId) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(quoteId.toString().getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (Exception exception) {
            throw new IllegalStateException(exception);
        }
    }

    private OrderProblem missingQuote() {
        return new OrderProblem(HttpStatus.NOT_FOUND, OrderProblem.NOT_FOUND, "Quote was not found");
    }

    private OrderProblem missingOrder() {
        return new OrderProblem(HttpStatus.NOT_FOUND, OrderProblem.NOT_FOUND, "Order was not found");
    }
}
