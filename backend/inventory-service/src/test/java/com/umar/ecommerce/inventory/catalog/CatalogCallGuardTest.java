package com.umar.ecommerce.inventory.catalog;

import com.umar.ecommerce.inventory.exception.InventoryProblem;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import io.github.resilience4j.retry.Retry;
import io.github.resilience4j.retry.RetryConfig;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CatalogCallGuardTest {

    @Test
    void retryWrapsTheBreakerAndDoesNotRetryBusinessOrOpenCircuit() {
        AtomicInteger attempts = new AtomicInteger();
        CircuitBreaker breaker = CircuitBreaker.of("inventoryCatalog", CircuitBreakerConfig.custom()
                .slidingWindowSize(4)
                .minimumNumberOfCalls(2)
                .failureRateThreshold(50)
                .waitDurationInOpenState(Duration.ofSeconds(5))
                .permittedNumberOfCallsInHalfOpenState(1)
                .recordExceptions(CatalogTechnicalException.class)
                .ignoreExceptions(InventoryProblem.class)
                .build());
        Retry retry = Retry.of("inventoryCatalog", RetryConfig.custom()
                .maxAttempts(2)
                .waitDuration(Duration.ofMillis(1))
                .retryExceptions(CatalogTechnicalException.class)
                .ignoreExceptions(io.github.resilience4j.circuitbreaker.CallNotPermittedException.class, InventoryProblem.class)
                .build());
        CatalogCallGuard guard = new CatalogCallGuard(breaker, retry);

        assertThatThrownBy(() -> guard.execute(() -> {
            attempts.incrementAndGet();
            throw new InventoryProblem(
                    org.springframework.http.HttpStatus.NOT_FOUND,
                    InventoryProblem.CATALOG_VARIANT_NOT_FOUND,
                    "missing"
            );
        })).isInstanceOf(InventoryProblem.class);
        assertThat(attempts).hasValue(1);
        assertThat(breaker.getMetrics().getNumberOfFailedCalls()).isZero();

        attempts.set(0);
        assertThatThrownBy(() -> guard.execute(() -> {
            attempts.incrementAndGet();
            throw new CatalogTechnicalException(CatalogTechnicalException.Kind.UNAVAILABLE, "down");
        })).extracting(error -> ((InventoryProblem) error).code())
                .isEqualTo(InventoryProblem.CATALOG_UNAVAILABLE);
        assertThat(attempts).hasValue(2);
        assertThat(breaker.getMetrics().getNumberOfFailedCalls()).isEqualTo(2);

        breaker.transitionToOpenState();
        attempts.set(0);
        assertThatThrownBy(() -> guard.execute(() -> {
            attempts.incrementAndGet();
            return "should-not-run";
        })).extracting(error -> ((InventoryProblem) error).code())
                .isEqualTo(InventoryProblem.CATALOG_UNAVAILABLE);
        assertThat(attempts).hasValue(0);

        breaker.transitionToHalfOpenState();
        assertThat(guard.execute(() -> "recovered")).isEqualTo("recovered");
        assertThat(breaker.getState()).isEqualTo(CircuitBreaker.State.CLOSED);
    }
}
