package com.lineless.queue_service.repository;

import com.lineless.queue_service.config.AbstractRepositoryTest;
import com.lineless.queue_service.entity.Business;
import com.lineless.queue_service.entity.Queue;
import com.lineless.queue_service.entity.QueueEntry;
import com.lineless.queue_service.entity.QueueSession;
import com.lineless.queue_service.enums.QueueEntryStatus;
import com.lineless.queue_service.enums.QueueSessionStatus;
import org.junit.jupiter.api.Test;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class QueueEntryRepositoryTest extends AbstractRepositoryTest {

    @Autowired
    private BusinessRepository businessRepository;

    @Autowired
    private QueueRepository queueRepository;

    @Autowired
    private QueueSessionRepository queueSessionRepository;

    @Autowired
    private QueueEntryRepository queueEntryRepository;

    @Test
    void shouldFindActiveCustomerEntryCountAndNextWaitingEntry() {

        Business business = createBusiness();
        Queue queue = createQueue(business);
        QueueSession session = createSession(queue);
        UUID customerUserId = UUID.randomUUID();

        QueueEntry earliestWaitingEntry = createEntry(
                session,
                UUID.randomUUID(),
                1,
                QueueEntryStatus.WAITING
        );
        QueueEntry activeCustomerEntry = createEntry(
                session,
                customerUserId,
                2,
                QueueEntryStatus.CALLED
        );
        createEntry(
                session,
                UUID.randomUUID(),
                3,
                QueueEntryStatus.WAITING
        );
        createEntry(session, customerUserId, 4, QueueEntryStatus.COMPLETED);

        long activeEntryCount = queueEntryRepository.countBySessionIdAndStatusIn(
                session.getId(),
                List.of(QueueEntryStatus.WAITING, QueueEntryStatus.CALLED)
        );
        QueueEntry firstActiveCustomerEntry = queueEntryRepository
                .findFirstBySessionIdAndCustomerUserIdAndStatusInOrderByTicketNumberAsc(
                        session.getId(),
                        customerUserId,
                        List.of(QueueEntryStatus.WAITING, QueueEntryStatus.CALLED)
                )
                .orElseThrow();
        QueueEntry firstWaitingEntry = queueEntryRepository
                .findFirstBySessionIdAndStatusOrderByTicketNumberAsc(
                        session.getId(),
                        QueueEntryStatus.WAITING
                )
                .orElseThrow();

        assertThat(activeEntryCount).isEqualTo(3);
        assertThat(firstActiveCustomerEntry.getId())
                .isEqualTo(activeCustomerEntry.getId());
        assertThat(firstWaitingEntry.getId()).isEqualTo(earliestWaitingEntry.getId());
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
        session.setNextTicketNumber(5);

        return queueSessionRepository.saveAndFlush(session);
    }

    private QueueEntry createEntry(
            QueueSession session,
            UUID customerUserId,
            int ticketNumber,
            QueueEntryStatus status
    ) {
        QueueEntry entry = BeanUtils.instantiateClass(QueueEntry.class);

        entry.setSession(session);
        entry.setCustomerUserId(customerUserId);
        entry.setTicketNumber(ticketNumber);
        entry.setTicketCode("T" + ticketNumber);
        entry.setStatus(status);

        return queueEntryRepository.saveAndFlush(entry);
    }
}
