package com.lineless.queue_service.exception;

public class DuplicateQueueNameException extends RuntimeException {

    public DuplicateQueueNameException(String name) {
        super("Queue name already exists in this business: " + name);
    }
}
