package com.umar.ecommerce.cart.catalog;

import com.umar.ecommerce.cart.exception.CartProblem;
import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.retry.Retry;
import org.springframework.http.HttpStatus;

import java.util.function.Supplier;

/**
 * Retry sits outside the circuit breaker, so each attempt is counted by the
 * breaker. {@link CallNotPermittedException} is not retried. Breaker metrics
 * therefore count HTTP attempts, not logical cart commands.
 */
public final class CatalogCallGuard {

    private final CircuitBreaker circuitBreaker;
    private final Retry retry;

    public CatalogCallGuard(CircuitBreaker circuitBreaker, Retry retry) {
        this.circuitBreaker = circuitBreaker;
        this.retry = retry;
    }

    public static CatalogCallGuard once() {
        return new CatalogCallGuard(null, null);
    }

    public <T> T execute(Supplier<T> call) {
        Supplier<T> attempt = circuitBreaker == null ? call : CircuitBreaker.decorateSupplier(circuitBreaker, call);
        Supplier<T> guarded = retry == null ? attempt : Retry.decorateSupplier(retry, attempt);
        try {
            return guarded.get();
        } catch (CallNotPermittedException exception) {
            throw new CartProblem(
                    HttpStatus.SERVICE_UNAVAILABLE,
                    CartProblem.CATALOG_UNAVAILABLE,
                    "Catalog circuit is open"
            );
        } catch (CatalogTechnicalException exception) {
            if (exception.kind() == CatalogTechnicalException.Kind.TIMEOUT) {
                return throwTimeout();
            }
            throw new CartProblem(
                    HttpStatus.SERVICE_UNAVAILABLE,
                    CartProblem.CATALOG_UNAVAILABLE,
                    "Catalog is unavailable"
            );
        }
    }

    private static <T> T throwTimeout() {
        throw new CartProblem(
                HttpStatus.GATEWAY_TIMEOUT,
                CartProblem.CATALOG_TIMEOUT,
                "Catalog did not respond in time"
        );
    }
}
