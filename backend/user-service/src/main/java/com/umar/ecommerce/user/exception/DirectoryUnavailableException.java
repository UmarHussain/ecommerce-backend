package com.umar.ecommerce.user.exception;

import org.springframework.http.HttpStatus;

public class DirectoryUnavailableException extends UserServiceException {

    public DirectoryUnavailableException(String message, Throwable cause) {
        super(HttpStatus.SERVICE_UNAVAILABLE, "USER_DIRECTORY_UNAVAILABLE", message);
        if (cause != null) {
            initCause(cause);
        }
    }

    public DirectoryUnavailableException(String message) {
        this(message, null);
    }
}
