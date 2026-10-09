package com.umar.ecommerce.cart.exception;

import org.springframework.http.HttpStatus;

public class CartProblem extends RuntimeException {

    public static final String VALIDATION_FAILED = "CART_VALIDATION_FAILED";
    public static final String MALFORMED_REQUEST = "CART_MALFORMED_REQUEST";
    public static final String AUTHENTICATION_REQUIRED = "CART_AUTHENTICATION_REQUIRED";
    public static final String ACCESS_DENIED = "CART_ACCESS_DENIED";
    public static final String STALE_VERSION = "CART_STALE_VERSION";
    public static final String ITEM_LIMIT = "CART_ITEM_LIMIT";
    public static final String LINE_NOT_FOUND = "CART_LINE_NOT_FOUND";
    public static final String SKU_UNAVAILABLE = "CART_SKU_UNAVAILABLE";
    public static final String REVALIDATION_REQUIRED = "CART_REVALIDATION_REQUIRED";
    public static final String CATALOG_UNAVAILABLE = "CART_CATALOG_UNAVAILABLE";
    public static final String CATALOG_TIMEOUT = "CART_CATALOG_TIMEOUT";
    public static final String COMMAND_IN_PROGRESS = "CART_COMMAND_IN_PROGRESS";
    public static final String INTERNAL_ERROR = "CART_INTERNAL_ERROR";

    private final HttpStatus status;
    private final String code;

    public CartProblem(HttpStatus status, String code, String message) {
        super(message);
        this.status = status;
        this.code = code;
    }

    public HttpStatus status() {
        return status;
    }

    public String code() {
        return code;
    }
}
