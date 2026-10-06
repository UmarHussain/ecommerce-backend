package com.umar.ecommerce.user.exception;

import org.springframework.http.HttpStatus;

public class UserServiceException extends RuntimeException {

    private final HttpStatus status;
    private final String code;

    public UserServiceException(HttpStatus status, String code, String message) {
        super(message);
        this.status = status;
        this.code = code;
    }

    public HttpStatus getStatus() {
        return status;
    }

    public String getCode() {
        return code;
    }
}
