package com.lineless.queue_service.repository;

import com.lineless.queue_service.entity.QueueEntry;
import com.lineless.queue_service.enums.QueueEntryStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface QueueEntryRepository extends JpaRepository<QueueEntry, UUID> {

    List<QueueEntry> findBySessionIdOrderByTicketNumberAsc(UUID sessionId);

    Optional<QueueEntry> findFirstBySessionIdAndCustomerUserIdAndStatusInOrderByTicketNumberAsc(
            UUID sessionId,
            UUID customerUserId,
            Collection<QueueEntryStatus> statuses
    );

    long countBySessionIdAndStatusIn(
            UUID sessionId,
            Collection<QueueEntryStatus> statuses
    );

    List<QueueEntry> findBySessionIdAndStatusIn(
            UUID sessionId,
            Collection<QueueEntryStatus> statuses
    );

    Optional<QueueEntry> findFirstBySessionIdAndStatusOrderByTicketNumberAsc(
            UUID sessionId,
            QueueEntryStatus status
    );
}
