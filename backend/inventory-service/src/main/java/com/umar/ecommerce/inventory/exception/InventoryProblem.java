package com.umar.ecommerce.inventory.exception;

import org.springframework.http.HttpStatus;

public class InventoryProblem extends RuntimeException {

    public static final String VALIDATION_FAILED = "INVENTORY_VALIDATION_FAILED";
    public static final String MALFORMED_REQUEST = "INVENTORY_MALFORMED_REQUEST";
    public static final String QUANTITY_OVERFLOW = "INVENTORY_QUANTITY_OVERFLOW";
    public static final String AUTHENTICATION_REQUIRED = "INVENTORY_AUTHENTICATION_REQUIRED";
    public static final String ACCESS_DENIED = "INVENTORY_ACCESS_DENIED";
    public static final String CATALOG_READ_REQUIRED = "INVENTORY_CATALOG_READ_REQUIRED";
    public static final String CATALOG_FORBIDDEN = "INVENTORY_CATALOG_FORBIDDEN";
    public static final String NOT_FOUND = "INVENTORY_NOT_FOUND";
    public static final String CATALOG_VARIANT_NOT_FOUND = "INVENTORY_CATALOG_VARIANT_NOT_FOUND";
    public static final String STALE_VERSION = "INVENTORY_STALE_VERSION";
    public static final String STOCK_INVARIANT = "INVENTORY_STOCK_INVARIANT";
    public static final String VARIANT_ALREADY_STOCKED = "INVENTORY_VARIANT_ALREADY_STOCKED";
    public static final String IDEMPOTENCY_CONFLICT = "INVENTORY_IDEMPOTENCY_CONFLICT";
    public static final String COMMAND_IN_PROGRESS = "INVENTORY_COMMAND_IN_PROGRESS";
    public static final String CATALOG_INACTIVE = "INVENTORY_CATALOG_INACTIVE";
    public static final String CATALOG_UNAVAILABLE = "INVENTORY_CATALOG_UNAVAILABLE";
    public static final String CATALOG_TIMEOUT = "INVENTORY_CATALOG_TIMEOUT";
    public static final String INTERNAL_ERROR = "INVENTORY_INTERNAL_ERROR";

    private final HttpStatus status;
    private final String code;

    public InventoryProblem(HttpStatus status, String code, String message) {
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
