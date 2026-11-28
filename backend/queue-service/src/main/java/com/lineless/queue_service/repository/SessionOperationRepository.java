package com.lineless.queue_service.repository;

import com.lineless.queue_service.entity.SessionOperation;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface SessionOperationRepository
        extends JpaRepository<SessionOperation, UUID> {

    List<SessionOperation> findBySessionIdOrderByCreatedAtAsc(UUID sessionId);
}
