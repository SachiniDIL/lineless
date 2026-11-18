package com.lineless.auth_service.exception;

public class InvalidGoogleTokenException extends RuntimeException {

    public InvalidGoogleTokenException(String message) {
        super(message);
    }
}
