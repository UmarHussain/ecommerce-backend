package com.umar.ecommerce.user.exception;

import org.springframework.http.HttpStatus;

public class NotFoundException extends UserServiceException {

    public NotFoundException(String message) {
        super(HttpStatus.NOT_FOUND, "USER_NOT_FOUND", message);
    }
}
