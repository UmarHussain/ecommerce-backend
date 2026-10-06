package com.umar.ecommerce.gateway.config;

import org.springframework.core.Ordered;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.cors.reactive.CorsUtils;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.UUID;
import java.util.regex.Pattern;

@Component
public class CorrelationIdFilter implements WebFilter, Ordered {

    static final String HEADER = "X-Correlation-ID";
    private static final Pattern SAFE_VALUE = Pattern.compile("^[A-Za-z0-9._-]{1,64}$");

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        String incoming = exchange.getRequest().getHeaders().getFirst(HEADER);
        String correlationId = incoming != null && SAFE_VALUE.matcher(incoming).matches()
                ? incoming
                : UUID.randomUUID().toString();
        ServerWebExchange mutated = exchange.mutate()
                .request(request -> request.headers(headers -> headers.set(HEADER, correlationId)))
                .build();
        mutated.getResponse().beforeCommit(() -> {
            HttpHeaders headers = mutated.getResponse().getHeaders();
            if (!headers.containsKey(HEADER)) {
                headers.set(HEADER, correlationId);
            }
            return Mono.empty();
        });
        if (CorsUtils.isPreFlightRequest(mutated.getRequest())) {
            String origin = mutated.getRequest().getHeaders().getOrigin();
            boolean apiPath = mutated.getRequest().getPath().value().startsWith("/api/");
            boolean allowed = apiPath && origin != null && AccessTokenRules.allowedCorsOrigins().contains(origin);
            HttpHeaders responseHeaders = mutated.getResponse().getHeaders();
            if (allowed) {
                responseHeaders.add(HttpHeaders.VARY, HttpHeaders.ORIGIN);
                responseHeaders.add(HttpHeaders.VARY, HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD);
                responseHeaders.add(HttpHeaders.VARY, HttpHeaders.ACCESS_CONTROL_REQUEST_HEADERS);
                responseHeaders.setAccessControlAllowOrigin(origin);
                responseHeaders.setAccessControlAllowMethods(List.of(
                        HttpMethod.GET, HttpMethod.POST, HttpMethod.PUT, HttpMethod.PATCH, HttpMethod.DELETE, HttpMethod.OPTIONS));
                responseHeaders.setAccessControlAllowHeaders(List.of(
                        "Authorization", "Content-Type", "Idempotency-Key", "X-Correlation-ID"));
                responseHeaders.setAccessControlExposeHeaders(List.of("X-Correlation-ID", "Location"));
                responseHeaders.setAccessControlMaxAge(600);
                mutated.getResponse().setStatusCode(HttpStatus.OK);
            } else {
                mutated.getResponse().setStatusCode(HttpStatus.FORBIDDEN);
            }
            return Mono.empty();
        }
        return chain.filter(mutated);
    }

    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE;
    }
}
