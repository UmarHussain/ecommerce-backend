package com.umar.ecommerce.user.exception;

import org.springframework.http.HttpStatus;

public class ForbiddenOperationException extends UserServiceException {

    public ForbiddenOperationException(String message) {
        super(HttpStatus.FORBIDDEN, "USER_FORBIDDEN", message);
    }
}
