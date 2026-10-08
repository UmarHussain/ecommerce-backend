package com.umar.ecommerce.catalog.exception;

public class ResourceConflictException extends RuntimeException {

    public static final String CODE = "CATALOG_RESOURCE_CONFLICT";
    public static final String STALE_VERSION = "CATALOG_STALE_VERSION";
    public static final String SKU_IMMUTABLE = "CATALOG_SKU_IMMUTABLE";

    private final String code;

    public ResourceConflictException(String message) {
        this(CODE, message);
    }

    public ResourceConflictException(String code, String message) {
        super(message);
        this.code = code;
    }

    public String getCode() {
        return code;
    }
}
