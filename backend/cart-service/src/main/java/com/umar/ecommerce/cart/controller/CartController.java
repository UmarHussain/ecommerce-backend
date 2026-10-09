package com.umar.ecommerce.cart.controller;

import com.umar.ecommerce.cart.dto.request.SetQuantityRequest;
import com.umar.ecommerce.cart.dto.response.CartResponse;
import com.umar.ecommerce.cart.exception.CartProblem;
import com.umar.ecommerce.cart.service.CartCommandService;
import com.umar.ecommerce.cart.service.Owner;
import com.umar.ecommerce.cart.web.CorrelationIdFilter;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/cart")
@Validated
@Tag(name = "Cart", description = "The caller's own cart. Ownership comes from the access token.")
@SecurityRequirement(name = "bearerAuth")
public class CartController {

    private final CartCommandService carts;

    public CartController(CartCommandService carts) {
        this.carts = carts;
    }

    @GetMapping
    @Operation(summary = "Get the caller's cart, creating an empty cart if needed")
    public CartResponse get(@AuthenticationPrincipal Jwt jwt, HttpServletRequest request) {
        return carts.get(owner(jwt), CorrelationIdFilter.correlationId(request));
    }

    @PutMapping("/items/{sku}")
    @Operation(summary = "Set the absolute quantity of a SKU. The same quantity does not change the version.")
    public CartResponse setQuantity(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable String sku,
            @Valid @RequestBody SetQuantityRequest body,
            HttpServletRequest request
    ) {
        return carts.setQuantity(
                owner(jwt),
                sku,
                body.quantity(),
                body.expectedVersion(),
                CorrelationIdFilter.correlationId(request)
        );
    }

    @DeleteMapping("/items/{sku}")
    @Operation(summary = "Remove one line. expectedVersion is a query parameter. Catalog is not required.")
    public CartResponse remove(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable String sku,
            @RequestParam @Min(0) long expectedVersion,
            HttpServletRequest request
    ) {
        return carts.remove(owner(jwt), sku, expectedVersion, CorrelationIdFilter.correlationId(request));
    }

    @DeleteMapping
    @Operation(summary = "Clear the caller's cart. expectedVersion is a query parameter. Catalog is not required.")
    public CartResponse clear(
            @AuthenticationPrincipal Jwt jwt,
            @RequestParam @Min(0) long expectedVersion,
            HttpServletRequest request
    ) {
        return carts.clear(owner(jwt), expectedVersion, CorrelationIdFilter.correlationId(request));
    }

    private static Owner owner(Jwt jwt) {
        if (jwt == null || jwt.getIssuer() == null || jwt.getSubject() == null || jwt.getSubject().isBlank()) {
            throw new CartProblem(
                    HttpStatus.UNAUTHORIZED,
                    CartProblem.AUTHENTICATION_REQUIRED,
                    "A valid bearer token is required"
            );
        }
        return new Owner(jwt.getIssuer().toString(), jwt.getSubject());
    }
}
