package com.lineless.queue_service.exception;

import java.util.UUID;

public class BusinessInUseException extends RuntimeException {

    public BusinessInUseException(UUID businessId) {
        super("Business cannot be deleted because it has queues: " + businessId);
    }
}
