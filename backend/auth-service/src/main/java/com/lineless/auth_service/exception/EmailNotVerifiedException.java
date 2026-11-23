package com.lineless.auth_service.exception;

public class EmailNotVerifiedException extends RuntimeException {

    public EmailNotVerifiedException() {
        super("Please verify your email address before logging in.");
    }
}