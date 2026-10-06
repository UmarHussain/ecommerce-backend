package com.umar.ecommerce.user.exception;

import org.springframework.http.HttpStatus;

public class InvalidRequestException extends UserServiceException {

    public InvalidRequestException(String message) {
        super(HttpStatus.BAD_REQUEST, "USER_INVALID_REQUEST", message);
    }
}
