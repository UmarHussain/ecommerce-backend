package com.umar.ecommerce.gateway.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.Ordered;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;

import java.nio.charset.StandardCharsets;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * In-memory request limit for API routes. It does not inspect bodies or call other services.
 */
@Component
public class ClientRateLimitFilter implements WebFilter, Ordered {

    private final boolean enabled;
    private final int requestsPerMinute;
    private final ConcurrentHashMap<String, Window> windows = new ConcurrentHashMap<>();

    public ClientRateLimitFilter(
            @Value("${platform.gateway.rate-limit.enabled:true}") boolean enabled,
            @Value("${platform.gateway.rate-limit.requests-per-minute:600}") int requestsPerMinute
    ) {
        this.enabled = enabled;
        this.requestsPerMinute = requestsPerMinute;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        String path = exchange.getRequest().getPath().value();
        if (!enabled || !path.startsWith("/api/")) {
            return chain.filter(exchange);
        }
        String client = exchange.getRequest().getRemoteAddress() == null
                ? "unknown"
                : exchange.getRequest().getRemoteAddress().getAddress().getHostAddress();
        long minute = System.currentTimeMillis() / 60_000L;
        Window window = windows.compute(client, (key, existing) -> {
            if (existing == null || existing.minute != minute) {
                return new Window(minute, new AtomicInteger(1));
            }
            existing.count.incrementAndGet();
            return existing;
        });
        if (windows.size() > 10_000) {
            windows.clear();
        }
        if (window.count.get() > requestsPerMinute) {
            exchange.getResponse().setStatusCode(HttpStatus.TOO_MANY_REQUESTS);
            exchange.getResponse().getHeaders().setContentType(MediaType.APPLICATION_JSON);
            exchange.getResponse().getHeaders().set("Retry-After", "60");
            byte[] body = "{\"code\":\"rate_limited\"}".getBytes(StandardCharsets.UTF_8);
            return exchange.getResponse().writeWith(Mono.just(exchange.getResponse().bufferFactory().wrap(body)));
        }
        return chain.filter(exchange);
    }

    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE + 10;
    }

    private record Window(long minute, AtomicInteger count) {
    }
}
