package com.umar.ecommerce.order.exception;

import org.springframework.http.HttpStatus;

public class OrderProblem extends RuntimeException {

    public static final String VALIDATION_FAILED = "ORDER_VALIDATION_FAILED";
    public static final String MALFORMED_REQUEST = "ORDER_MALFORMED_REQUEST";
    public static final String AUTHENTICATION_REQUIRED = "ORDER_AUTHENTICATION_REQUIRED";
    public static final String ACCESS_DENIED = "ORDER_ACCESS_DENIED";
    public static final String NOT_FOUND = "ORDER_NOT_FOUND";
    public static final String REVIEW_REQUIRED = "ORDER_REVIEW_REQUIRED";
    public static final String IDEMPOTENCY_CONFLICT = "ORDER_IDEMPOTENCY_CONFLICT";
    public static final String IDEMPOTENCY_IN_PROGRESS = "ORDER_IDEMPOTENCY_IN_PROGRESS";
    public static final String NOT_CANCELLABLE = "ORDER_NOT_CANCELLABLE";
    public static final String UPSTREAM_UNAVAILABLE = "ORDER_UPSTREAM_UNAVAILABLE";
    public static final String UPSTREAM_TIMEOUT = "ORDER_UPSTREAM_TIMEOUT";
    public static final String INTERNAL_ERROR = "ORDER_INTERNAL_ERROR";

    private final HttpStatus status;
    private final String code;
    private final String reason;

    public OrderProblem(HttpStatus status, String code, String message) {
        this(status, code, message, null);
    }

    public OrderProblem(HttpStatus status, String code, String message, String reason) {
        super(message);
        this.status = status;
        this.code = code;
        this.reason = reason;
    }

    public HttpStatus status() {
        return status;
    }

    public String code() {
        return code;
    }

    public String reason() {
        return reason;
    }

    public static OrderProblem review(String reason, String message) {
        return new OrderProblem(HttpStatus.CONFLICT, REVIEW_REQUIRED, message, reason);
    }
}
