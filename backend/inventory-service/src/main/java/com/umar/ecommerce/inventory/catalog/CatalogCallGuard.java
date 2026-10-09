package com.umar.ecommerce.inventory.catalog;

import com.umar.ecommerce.inventory.exception.InventoryProblem;
import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.retry.Retry;
import org.springframework.http.HttpStatus;

import java.util.function.Supplier;

/**
 * Retry sits outside the circuit breaker. Each HTTP attempt is one breaker
 * call. {@link CallNotPermittedException} is not retried. A 404, 401, 403,
 * or inactive catalog result is an {@link InventoryProblem} and is neither
 * retried nor counted as a breaker failure.
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
            throw new InventoryProblem(
                    HttpStatus.SERVICE_UNAVAILABLE,
                    InventoryProblem.CATALOG_UNAVAILABLE,
                    "Catalog circuit is open"
            );
        } catch (CatalogTechnicalException exception) {
            if (exception.kind() == CatalogTechnicalException.Kind.TIMEOUT) {
                throw new InventoryProblem(
                        HttpStatus.GATEWAY_TIMEOUT,
                        InventoryProblem.CATALOG_TIMEOUT,
                        "Catalog did not respond in time"
                );
            }
            throw new InventoryProblem(
                    HttpStatus.SERVICE_UNAVAILABLE,
                    InventoryProblem.CATALOG_UNAVAILABLE,
                    "Catalog is unavailable"
            );
        }
    }
}
