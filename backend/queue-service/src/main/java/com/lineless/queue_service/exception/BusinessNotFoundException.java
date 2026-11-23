package com.lineless.queue_service.exception;

import java.util.UUID;

public class BusinessNotFoundException extends RuntimeException {

    public BusinessNotFoundException(UUID businessId) {
        super("Business not found: " + businessId);
    }
}
