package com.lineless.queue_service.repository;

import com.lineless.queue_service.entity.QueueSession;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface QueueSessionRepository extends JpaRepository<QueueSession, UUID> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select s from QueueSession s where s.id = :sessionId")
    Optional<QueueSession> findByIdForUpdate(@Param("sessionId") UUID sessionId);

    List<QueueSession> findByQueueId(UUID queueId);

    Optional<QueueSession> findByQueueIdAndSessionDate(UUID queueId, LocalDate sessionDate);

    List<QueueSession> findByQueueIdOrderBySessionDateAsc(UUID queueId);

    boolean existsByQueueId(UUID queueId);
}
