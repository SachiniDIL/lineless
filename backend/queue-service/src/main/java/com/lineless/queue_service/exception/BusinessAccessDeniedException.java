package com.lineless.queue_service.exception;

import java.util.UUID;

public class BusinessAccessDeniedException extends RuntimeException {

    public BusinessAccessDeniedException(UUID businessId) {
        super("User cannot modify business: " + businessId);
    }
}
