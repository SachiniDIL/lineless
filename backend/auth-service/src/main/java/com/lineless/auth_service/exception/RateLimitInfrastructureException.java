package com.lineless.auth_service.exception;

public class RateLimitInfrastructureException extends RuntimeException {

    public RateLimitInfrastructureException(Throwable cause) {
        super("Rate limiting service is temporarily unavailable.", cause);
    }
}