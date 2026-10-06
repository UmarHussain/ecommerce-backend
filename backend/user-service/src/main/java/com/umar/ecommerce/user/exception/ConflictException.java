package com.umar.ecommerce.user.exception;

import org.springframework.http.HttpStatus;

public class ConflictException extends UserServiceException {

    public ConflictException(String message) {
        super(HttpStatus.CONFLICT, "USER_CONFLICT", message);
    }

    public ConflictException(String code, String message) {
        super(HttpStatus.CONFLICT, code, message);
    }
}
