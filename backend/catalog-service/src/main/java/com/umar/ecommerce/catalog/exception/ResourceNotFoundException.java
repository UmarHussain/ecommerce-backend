package com.umar.ecommerce.catalog.exception;

public class ResourceNotFoundException extends RuntimeException {

    public static final String CODE = "CATALOG_RESOURCE_NOT_FOUND";

    public ResourceNotFoundException(String message) {
        super(message);
    }

    public String getCode() {
        return CODE;
    }
}
