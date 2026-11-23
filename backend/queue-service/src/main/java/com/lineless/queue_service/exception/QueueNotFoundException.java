package com.lineless.queue_service.exception;

import java.util.UUID;

public class QueueNotFoundException extends RuntimeException {

    public QueueNotFoundException(UUID queueId) {
        super("Queue not found: " + queueId);
    }
}
