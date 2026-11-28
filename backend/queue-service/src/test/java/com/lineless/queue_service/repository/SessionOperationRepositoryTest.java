package com.lineless.queue_service.repository;

import com.lineless.queue_service.config.AbstractRepositoryTest;
import com.lineless.queue_service.entity.Business;
import com.lineless.queue_service.entity.Queue;
import com.lineless.queue_service.entity.QueueSession;
import com.lineless.queue_service.entity.SessionOperation;
import com.lineless.queue_service.enums.QueueSessionStatus;
import com.lineless.queue_service.enums.SessionOperationType;
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

class SessionOperationRepositoryTest extends AbstractRepositoryTest {

    @Autowired
    private BusinessRepository businessRepository;

    @Autowired
    private QueueRepository queueRepository;

    @Autowired
    private QueueSessionRepository queueSessionRepository;

    @Autowired
    private SessionOperationRepository sessionOperationRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private EntityManager entityManager;

    @Test
    void shouldFindSessionOperationsInCreationTimeOrder() {

        Business business = createBusiness();
        Queue queue = createQueue(business);
        QueueSession session = createSession(queue);

        SessionOperation laterOperation = createOperation(
                session,
                SessionOperationType.PAUSED
        );
        SessionOperation earlierOperation = createOperation(
                session,
                SessionOperationType.OPENED
        );

        Instant earlierCreatedAt = Instant.parse("2026-01-01T10:00:00Z");
        Instant laterCreatedAt = Instant.parse("2026-01-01T10:05:00Z");
        setCreatedAt(earlierOperation, earlierCreatedAt);
        setCreatedAt(laterOperation, laterCreatedAt);
        entityManager.clear();

        List<SessionOperation> operations = sessionOperationRepository
                .findBySessionIdOrderByCreatedAtAsc(session.getId());

        assertThat(operations)
                .extracting(SessionOperation::getId)
                .containsExactly(earlierOperation.getId(), laterOperation.getId());
        assertThat(operations)
                .extracting(SessionOperation::getCreatedAt)
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
        session.setNextTicketNumber(1);

        return queueSessionRepository.saveAndFlush(session);
    }

    private SessionOperation createOperation(
            QueueSession session,
            SessionOperationType operationType
    ) {
        SessionOperation operation = BeanUtils.instantiateClass(SessionOperation.class);

        operation.setSession(session);
        operation.setOperationType(operationType);
        operation.setPerformedByUserId(UUID.randomUUID());

        return sessionOperationRepository.saveAndFlush(operation);
    }

    private void setCreatedAt(SessionOperation operation, Instant createdAt) {
        jdbcTemplate.update(
                "UPDATE session_operations SET created_at = ? WHERE id = ?",
                Timestamp.from(createdAt),
                operation.getId()
        );
    }
}
