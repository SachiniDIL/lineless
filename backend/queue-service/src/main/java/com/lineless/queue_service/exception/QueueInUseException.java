package com.lineless.queue_service.exception;

import java.util.UUID;

public class QueueInUseException extends RuntimeException {

    public QueueInUseException(UUID queueId) {
        super("Queue cannot be deleted because it has sessions: " + queueId);
    }
}
