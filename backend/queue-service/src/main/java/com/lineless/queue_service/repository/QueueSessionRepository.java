package com.lineless.queue_service.repository;

import com.lineless.queue_service.entity.QueueSession;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface QueueSessionRepository extends JpaRepository<QueueSession, UUID> {

    Optional<QueueSession> findByQueueIdAndSessionDate(UUID queueId, LocalDate sessionDate);

    List<QueueSession> findByQueueIdOrderBySessionDateAsc(UUID queueId);

    boolean existsByQueueId(UUID queueId);
}
