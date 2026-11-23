package com.lineless.queue_service.repository;

import com.lineless.queue_service.entity.QueueOperation;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface QueueOperationRepository extends JpaRepository<QueueOperation, UUID> {

    List<QueueOperation> findByQueueEntryIdOrderByCreatedAtAsc(UUID queueEntryId);
}
