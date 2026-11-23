package com.lineless.queue_service.exception;

import java.util.UUID;

public class QueueAccessDeniedException extends RuntimeException {

    public QueueAccessDeniedException(UUID queueId) {
        super("User cannot manage queue: " + queueId);
    }
}
