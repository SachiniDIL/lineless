package com.lineless.queue_service.repository;

import com.lineless.queue_service.config.AbstractRepositoryTest;
import com.lineless.queue_service.entity.Business;
import com.lineless.queue_service.entity.Queue;
import com.lineless.queue_service.entity.QueueSession;
import org.junit.jupiter.api.Test;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class QueueSessionRepositoryTest extends AbstractRepositoryTest {

    @Autowired
    private BusinessRepository businessRepository;

    @Autowired
    private QueueRepository queueRepository;

    @Autowired
    private QueueSessionRepository queueSessionRepository;

    @Test
    void shouldFindSessionsByQueueId() {

        Business business = createBusiness();
        Queue queue = createQueue(business);

        QueueSession firstSession = createSession(queue, LocalDate.now());
        QueueSession secondSession = createSession(
                queue,
                LocalDate.now().plusDays(1)
        );

        List<QueueSession> sessions =
                queueSessionRepository.findByQueueId(queue.getId());

        assertThat(sessions)
                .extracting(QueueSession::getId)
                .containsExactlyInAnyOrder(
                        firstSession.getId(),
                        secondSession.getId()
                );
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

    private QueueSession createSession(Queue queue, LocalDate sessionDate) {
        QueueSession session = BeanUtils.instantiateClass(QueueSession.class);

        session.setQueue(queue);
        session.setSessionDate(sessionDate);
        session.setStatus(
                com.lineless.queue_service.enums.QueueSessionStatus.OPEN
        );
        session.setCapacity(50);
        session.setScheduledStartAt(
                sessionDate.atStartOfDay(
                        java.time.ZoneId.of("Asia/Colombo")
                ).toInstant()
        );
        session.setNextTicketNumber(1);

        return queueSessionRepository.saveAndFlush(session);
    }
}
