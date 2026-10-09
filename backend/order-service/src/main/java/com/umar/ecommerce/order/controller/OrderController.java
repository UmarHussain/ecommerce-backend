package com.umar.ecommerce.order.controller;

import com.umar.ecommerce.order.dto.response.OrderPageResponse;
import com.umar.ecommerce.order.dto.response.OrderResponse;
import com.umar.ecommerce.order.dto.response.QuoteResponse;
import com.umar.ecommerce.order.exception.OrderProblem;
import com.umar.ecommerce.order.service.CheckoutResult;
import com.umar.ecommerce.order.service.QuoteCheckoutService;
import com.umar.ecommerce.order.web.CorrelationIdFilter;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/orders")
public class OrderController {

    private final QuoteCheckoutService checkout;

    public OrderController(QuoteCheckoutService checkout) {
        this.checkout = checkout;
    }

    @PostMapping("/quotes")
    public QuoteResponse quote(
            @AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody QuoteRequest request,
            HttpServletRequest http
    ) {
        Owner owner = owner(jwt);
        return checkout.quote(
                owner.issuer(),
                owner.subject(),
                bearer(http),
                request.expectedCartVersion().longValue(),
                request.addressId(),
                CorrelationIdFilter.correlationId(http)
        );
    }

    @PostMapping
    public ResponseEntity<String> accept(
            @AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody AcceptRequest request,
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            HttpServletRequest http
    ) {
        Owner owner = owner(jwt);
        CheckoutResult result = checkout.accept(
                owner.issuer(),
                owner.subject(),
                bearer(http),
                request.quoteId(),
                idempotencyKey,
                CorrelationIdFilter.correlationId(http)
        );
        ResponseEntity.BodyBuilder builder = ResponseEntity.status(result.status()).contentType(MediaType.APPLICATION_JSON);
        if (result.location() != null) {
            builder.header("Location", result.location());
        }
        return builder.body(result.body());
    }

    @GetMapping
    public OrderPageResponse list(
            @AuthenticationPrincipal Jwt jwt,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        Owner owner = owner(jwt);
        return checkout.list(owner.issuer(), owner.subject(), page, size);
    }

    @GetMapping("/{orderId}")
    public OrderResponse get(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID orderId) {
        Owner owner = owner(jwt);
        return checkout.get(owner.issuer(), owner.subject(), orderId);
    }

    @PostMapping("/{orderId}/cancel")
    public ResponseEntity<OrderResponse> cancel(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID orderId) {
        Owner owner = owner(jwt);
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(checkout.cancel(owner.issuer(), owner.subject(), orderId));
    }

    private static String bearer(HttpServletRequest request) {
        String header = request.getHeader("Authorization");
        if (header == null || !header.startsWith("Bearer ")) {
            throw new OrderProblem(HttpStatus.UNAUTHORIZED, OrderProblem.AUTHENTICATION_REQUIRED, "A valid bearer token is required");
        }
        return header;
    }

    private static Owner owner(Jwt jwt) {
        if (jwt == null || jwt.getIssuer() == null || jwt.getSubject() == null || jwt.getSubject().isBlank()) {
            throw new OrderProblem(HttpStatus.UNAUTHORIZED, OrderProblem.AUTHENTICATION_REQUIRED, "A valid bearer token is required");
        }
        return new Owner(jwt.getIssuer().toString(), jwt.getSubject());
    }

    public record QuoteRequest(@NotNull Long expectedCartVersion, @NotNull UUID addressId) {
    }

    public record AcceptRequest(@NotNull UUID quoteId) {
    }

    private record Owner(String issuer, String subject) {
    }
}
