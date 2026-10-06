package com.umar.ecommerce.catalog.exception;

public class InvalidRequestException extends RuntimeException {

    public static final String CODE = "CATALOG_INVALID_REQUEST";

    public InvalidRequestException(String message) {
        super(message);
    }

    public InvalidRequestException(String message, Throwable cause) {
        super(message, cause);
    }

    public String getCode() {
        return CODE;
    }
}
