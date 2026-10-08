package com.umar.ecommerce.gateway.config;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.core.annotation.Order;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebExceptionHandler;
import reactor.core.publisher.Mono;

import java.net.ConnectException;
import java.net.UnknownHostException;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeoutException;

/**
 * Maps catalog and other downstream transport failures to 502, 503, or 504.
 * A response that catalog-service already wrote is left unchanged.
 */
@Component
@Order(-2)
public class DownstreamErrorWebExceptionHandler implements WebExceptionHandler {

    private final ObjectMapper objectMapper;

    public DownstreamErrorWebExceptionHandler(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    @NonNull
    public Mono<Void> handle(@NonNull ServerWebExchange exchange, @NonNull Throwable ex) {
        HttpStatus status = DownstreamStatus.resolve(ex);
        if (status == null || exchange.getResponse().isCommitted()) {
            return Mono.error(ex);
        }
        String path = exchange.getRequest().getPath().value();
        if (!path.startsWith("/api/")) {
            return Mono.error(ex);
        }
        String correlationId = exchange.getRequest().getHeaders().getFirst(CorrelationIdFilter.HEADER);
        if (correlationId == null || correlationId.isBlank()) {
            correlationId = UUID.randomUUID().toString();
        }
        byte[] body = problem(status, path, correlationId);
        exchange.getResponse().setStatusCode(status);
        exchange.getResponse().getHeaders().setContentType(MediaType.APPLICATION_PROBLEM_JSON);
        exchange.getResponse().getHeaders().set(CorrelationIdFilter.HEADER, correlationId);
        DataBuffer buffer = exchange.getResponse().bufferFactory().wrap(body);
        return exchange.getResponse().writeWith(Mono.just(buffer));
    }

    private byte[] problem(HttpStatus status, String path, String correlationId) {
        String code = switch (status) {
            case SERVICE_UNAVAILABLE -> "GATEWAY_DOWNSTREAM_UNAVAILABLE";
            case GATEWAY_TIMEOUT -> "GATEWAY_DOWNSTREAM_TIMEOUT";
            default -> "GATEWAY_DOWNSTREAM_ERROR";
        };
        String detail = switch (status) {
            case SERVICE_UNAVAILABLE -> "The downstream service is unavailable";
            case GATEWAY_TIMEOUT -> "The downstream service timed out";
            default -> "The downstream service failed";
        };
        Map<String, Object> problem = new LinkedHashMap<>();
        problem.put("title", status.getReasonPhrase());
        problem.put("status", status.value());
        problem.put("detail", detail);
        problem.put("code", code);
        problem.put("path", path);
        problem.put("correlationId", correlationId);
        try {
            return objectMapper.writeValueAsBytes(problem);
        } catch (JsonProcessingException failure) {
            return detail.getBytes(StandardCharsets.UTF_8);
        }
    }
}

final class DownstreamStatus {

    private DownstreamStatus() {
    }

    static HttpStatus resolve(Throwable error) {
        boolean timeout = false;
        boolean connect = false;
        boolean io = false;
        Throwable current = error;
        while (current != null) {
            String name = current.getClass().getName();
            if (current instanceof ConnectException
                    || current instanceof UnknownHostException
                    || name.contains("AnnotatedConnectException")) {
                connect = true;
            }
            if (current instanceof TimeoutException
                    || name.endsWith("ReadTimeoutException")
                    || name.endsWith("TimeoutException")) {
                timeout = true;
            }
            if (current instanceof java.io.IOException
                    || name.contains("PrematureCloseException")
                    || name.endsWith("WebClientRequestException")) {
                io = true;
            }
            current = current.getCause();
        }
        if (connect) {
            return HttpStatus.SERVICE_UNAVAILABLE;
        }
        if (timeout) {
            return HttpStatus.GATEWAY_TIMEOUT;
        }
        if (io) {
            return HttpStatus.BAD_GATEWAY;
        }
        return null;
    }
}
