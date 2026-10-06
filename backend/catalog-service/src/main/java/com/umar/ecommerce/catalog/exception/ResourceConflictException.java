package com.umar.ecommerce.catalog.exception;

public class ResourceConflictException extends RuntimeException {

    public static final String CODE = "CATALOG_RESOURCE_CONFLICT";

    public ResourceConflictException(String message) {
        super(message);
    }

    public String getCode() {
        return CODE;
    }
}
