package com.umar.ecommerce.order.remote;

import com.umar.ecommerce.order.exception.OrderProblem;
import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.retry.Retry;
import org.springframework.http.HttpStatus;

import java.util.function.Supplier;

public final class CheckoutCallGuard {

    private final CircuitBreaker circuitBreaker;
    private final Retry retry;

    public CheckoutCallGuard(CircuitBreaker circuitBreaker, Retry retry) {
        this.circuitBreaker = circuitBreaker;
        this.retry = retry;
    }

    public <T> T execute(Supplier<T> call) {
        Supplier<T> attempt = CircuitBreaker.decorateSupplier(circuitBreaker, call);
        Supplier<T> guarded = Retry.decorateSupplier(retry, attempt);
        try {
            return guarded.get();
        } catch (CallNotPermittedException exception) {
            throw new OrderProblem(HttpStatus.SERVICE_UNAVAILABLE, OrderProblem.UPSTREAM_UNAVAILABLE, "A required service is unavailable");
        } catch (CheckoutRemote.OrderUpstreamException exception) {
            if (exception.timeout()) {
                throw new OrderProblem(HttpStatus.GATEWAY_TIMEOUT, OrderProblem.UPSTREAM_TIMEOUT, "A required service did not respond in time");
            }
            throw new OrderProblem(HttpStatus.SERVICE_UNAVAILABLE, OrderProblem.UPSTREAM_UNAVAILABLE, "A required service is unavailable");
        }
    }
}
