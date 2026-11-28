package com.lineless.queue_service.repository;

import com.lineless.queue_service.config.AbstractRepositoryTest;
import com.lineless.queue_service.entity.*;
import com.lineless.queue_service.enums.QueueEntryStatus;
import com.lineless.queue_service.enums.QueueOperationType;
import com.lineless.queue_service.enums.QueueSessionStatus;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class QueueOperationRepositoryTest extends AbstractRepositoryTest {

    @Autowired
    private BusinessRepository businessRepository;

    @Autowired
    private QueueRepository queueRepository;

    @Autowired
    private QueueSessionRepository queueSessionRepository;

    @Autowired
    private QueueEntryRepository queueEntryRepository;

    @Autowired
    private QueueOperationRepository queueOperationRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private EntityManager entityManager;

    @Test
    void shouldFindOperationsByQueueEntryInCreationTimeOrder() {

        Business business = createBusiness();
        Queue queue = createQueue(business);
        QueueSession session = createSession(queue);
        QueueEntry entry = createEntry(session);

        QueueOperation laterOperation = createOperation(
                entry,
                QueueOperationType.CALLED
        );
        QueueOperation earlierOperation = createOperation(
                entry,
                QueueOperationType.JOINED
        );

        Instant earlierCreatedAt = Instant.parse("2026-01-01T10:00:00Z");
        Instant laterCreatedAt = Instant.parse("2026-01-01T10:05:00Z");
        setCreatedAt(earlierOperation, earlierCreatedAt);
        setCreatedAt(laterOperation, laterCreatedAt);
        entityManager.clear();

        List<QueueOperation> operations = queueOperationRepository
                .findByQueueEntryIdOrderByCreatedAtAsc(entry.getId());

        assertThat(operations)
                .extracting(QueueOperation::getId)
                .containsExactly(earlierOperation.getId(), laterOperation.getId());
        assertThat(operations)
                .extracting(QueueOperation::getCreatedAt)
                .containsExactly(earlierCreatedAt, laterCreatedAt);
    }

    private Business createBusiness() {
        Business business = BeanUtils.instantiateClass(Business.class);

        business.setOwnerUserId(UUID.randomUUID());
        business.setName("Test Business");
        business.setPublic(true);

        return businessRepository.saveAndFlush(business);
    }

    private Queue createQueue(Business business) {
        Queue queue = BeanUtils.instantiateClass(Queue.class);

        queue.setBusiness(business);
        queue.setName("General");
        queue.setAverageServiceTimeMinutes(10);
        queue.setMaxCapacity(50);

        return queueRepository.saveAndFlush(queue);
    }

    private QueueSession createSession(Queue queue) {
        QueueSession session = BeanUtils.instantiateClass(QueueSession.class);
        LocalDate sessionDate = LocalDate.now();

        session.setQueue(queue);
        session.setSessionDate(sessionDate);
        session.setStatus(QueueSessionStatus.OPEN);
        session.setCapacity(50);
        session.setScheduledStartAt(
                sessionDate.atStartOfDay(ZoneId.of("Asia/Colombo")).toInstant()
        );
        session.setNextTicketNumber(2);

        return queueSessionRepository.saveAndFlush(session);
    }

    private QueueEntry createEntry(QueueSession session) {
        QueueEntry entry = BeanUtils.instantiateClass(QueueEntry.class);

        entry.setSession(session);
        entry.setCustomerUserId(UUID.randomUUID());
        entry.setTicketNumber(1);
        entry.setTicketCode("T1");
        entry.setStatus(QueueEntryStatus.WAITING);

        return queueEntryRepository.saveAndFlush(entry);
    }

    private QueueOperation createOperation(
            QueueEntry entry,
            QueueOperationType operationType
    ) {
        QueueOperation operation = BeanUtils.instantiateClass(QueueOperation.class);

        operation.setQueueEntry(entry);
        operation.setOperationType(operationType);
        operation.setPerformedByUserId(UUID.randomUUID());

        return queueOperationRepository.saveAndFlush(operation);
    }

    private void setCreatedAt(QueueOperation operation, Instant createdAt) {
        jdbcTemplate.update(
                "UPDATE queue_operations SET created_at = ? WHERE id = ?",
                Timestamp.from(createdAt),
                operation.getId()
        );
    }
}
