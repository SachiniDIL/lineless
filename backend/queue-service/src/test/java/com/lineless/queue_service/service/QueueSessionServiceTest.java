package com.lineless.queue_service.service;

import com.lineless.queue_service.entity.Business;
import com.lineless.queue_service.entity.OutboxEvent;
import com.lineless.queue_service.entity.Queue;
import com.lineless.queue_service.entity.QueueEntry;
import com.lineless.queue_service.entity.QueueOperation;
import com.lineless.queue_service.entity.QueueSession;
import com.lineless.queue_service.entity.SessionOperation;
import com.lineless.queue_service.enums.QueueEntryStatus;
import com.lineless.queue_service.enums.QueueOperationType;
import com.lineless.queue_service.enums.QueueSessionStatus;
import com.lineless.queue_service.enums.SessionOperationType;
import com.lineless.queue_service.exception.BusinessAccessDeniedException;
import com.lineless.queue_service.exception.QueueValidationException;
import com.lineless.queue_service.repository.OutboxEventRepository;
import com.lineless.queue_service.repository.QueueEntryRepository;
import com.lineless.queue_service.repository.QueueOperationRepository;
import com.lineless.queue_service.repository.QueueRepository;
import com.lineless.queue_service.repository.QueueSessionRepository;
import com.lineless.queue_service.repository.SessionOperationRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.BeanUtils;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class QueueSessionServiceTest {

    private static final ZoneId BUSINESS_ZONE = ZoneId.of("Asia/Colombo");
    private static final LocalDate SESSION_DATE = LocalDate.now(BUSINESS_ZONE);

    @Mock
    private QueueSessionRepository queueSessionRepository;

    @Mock
    private QueueRepository queueRepository;

    @Mock
    private QueueEntryRepository queueEntryRepository;

    @Mock
    private QueueOperationRepository queueOperationRepository;

    @Mock
    private SessionOperationRepository sessionOperationRepository;

    @Mock
    private OutboxEventRepository outboxEventRepository;

    private QueueSessionService queueSessionService;

    @BeforeEach
    void setUp() {
        queueSessionService = new QueueSessionService(
                queueSessionRepository,
                queueRepository,
                queueEntryRepository,
                queueOperationRepository,
                sessionOperationRepository,
                outboxEventRepository
        );
        lenient().when(queueSessionRepository.save(any(QueueSession.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        lenient().when(queueSessionRepository.saveAndFlush(any(QueueSession.class)))
                .thenAnswer(invocation -> {
                    QueueSession session = invocation.getArgument(0);
                    if (session.getId() == null) {
                        session.setId(UUID.randomUUID());
                    }
                    return session;
                });
    }

    @Test
    void shouldOpenSessionWithQueueCapacityAndAudit() {
        UUID ownerId = UUID.randomUUID();
        Queue queue = createQueue(ownerId);
        when(queueRepository.findByIdForUpdate(queue.getId()))
                .thenReturn(Optional.of(queue));
        when(queueSessionRepository.findByQueueIdAndSessionDate(
                queue.getId(), SESSION_DATE
        )).thenReturn(Optional.empty());

        QueueSession opened = queueSessionService.openSession(
                queue.getId(),
                ownerId,
                SESSION_DATE,
                SESSION_DATE.atTime(9, 0).atZone(BUSINESS_ZONE).toInstant()
        );

        assertThat(opened.getStatus()).isEqualTo(QueueSessionStatus.OPEN);
        assertThat(opened.getCapacity()).isEqualTo(queue.getMaxCapacity());
        assertThat(opened.getSessionDate()).isEqualTo(SESSION_DATE);
        assertThat(opened.getScheduledStartAt())
                .isEqualTo(SESSION_DATE.atTime(9, 0).atZone(BUSINESS_ZONE).toInstant());
        assertThat(opened.getOpenedAt()).isNotNull();
        verify(sessionOperationRepository).save(any(SessionOperation.class));
        verify(outboxEventRepository).save(any(OutboxEvent.class));
    }

    @Test
    void shouldReturnExistingOpenSessionWithoutDuplicateAuditOrEvent() {
        UUID ownerId = UUID.randomUUID();
        Queue queue = createQueue(ownerId);
        queue.setActive(false);
        QueueSession existing = createSession(queue, QueueSessionStatus.OPEN);
        when(queueRepository.findByIdForUpdate(queue.getId()))
                .thenReturn(Optional.of(queue));
        when(queueSessionRepository.findByQueueIdAndSessionDate(
                queue.getId(), SESSION_DATE
        )).thenReturn(Optional.of(existing));

        QueueSession result = queueSessionService.openSession(
                queue.getId(),
                ownerId,
                SESSION_DATE,
                SESSION_DATE.atStartOfDay(BUSINESS_ZONE).toInstant()
        );

        assertThat(result).isSameAs(existing);
        verifyNoInteractions(sessionOperationRepository, outboxEventRepository);
    }

    @Test
    void shouldRejectOpeningForInactiveQueue() {
        UUID ownerId = UUID.randomUUID();
        Queue queue = createQueue(ownerId);
        queue.setActive(false);
        when(queueRepository.findByIdForUpdate(queue.getId()))
                .thenReturn(Optional.of(queue));

        assertThatThrownBy(() -> queueSessionService.openSession(
                queue.getId(), ownerId, SESSION_DATE,
                SESSION_DATE.atStartOfDay(BUSINESS_ZONE).toInstant()
        )).isInstanceOf(QueueValidationException.class)
                .hasMessageContaining("inactive queue");

        verify(queueSessionRepository, never()).saveAndFlush(any(QueueSession.class));
    }

    @Test
    void shouldRejectOpeningWhenAnotherSessionExistsForQueueDate() {
        UUID ownerId = UUID.randomUUID();
        Queue queue = createQueue(ownerId);
        QueueSession paused = createSession(queue, QueueSessionStatus.PAUSED);
        when(queueRepository.findByIdForUpdate(queue.getId()))
                .thenReturn(Optional.of(queue));
        when(queueSessionRepository.findByQueueIdAndSessionDate(
                queue.getId(), SESSION_DATE
        )).thenReturn(Optional.of(paused));

        assertThatThrownBy(() -> queueSessionService.openSession(
                queue.getId(), ownerId, SESSION_DATE,
                SESSION_DATE.atStartOfDay(BUSINESS_ZONE).toInstant()
        )).isInstanceOf(QueueValidationException.class)
                .hasMessageContaining("already exists");

        verify(queueSessionRepository, never()).saveAndFlush(any(QueueSession.class));
    }

    @Test
    void shouldRejectScheduledStartOnDifferentBusinessDate() {
        UUID ownerId = UUID.randomUUID();
        Queue queue = createQueue(ownerId);
        when(queueRepository.findByIdForUpdate(queue.getId()))
                .thenReturn(Optional.of(queue));

        assertThatThrownBy(() -> queueSessionService.openSession(
                queue.getId(), ownerId, SESSION_DATE,
                SESSION_DATE.plusDays(1).atStartOfDay(BUSINESS_ZONE).toInstant()
        )).isInstanceOf(QueueValidationException.class)
                .hasMessageContaining("business date");
    }

    @Test
    void shouldRejectOpeningForPastBusinessDate() {
        UUID ownerId = UUID.randomUUID();
        Queue queue = createQueue(ownerId);
        LocalDate pastDate = SESSION_DATE.minusDays(1);
        when(queueRepository.findByIdForUpdate(queue.getId()))
                .thenReturn(Optional.of(queue));
        when(queueSessionRepository.findByQueueIdAndSessionDate(queue.getId(), pastDate))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> queueSessionService.openSession(
                queue.getId(),
                ownerId,
                pastDate,
                pastDate.atTime(9, 0).atZone(BUSINESS_ZONE).toInstant()
        )).isInstanceOf(QueueValidationException.class)
                .hasMessageContaining("past business date");

        verify(queueSessionRepository, never()).saveAndFlush(any(QueueSession.class));
    }

    @Test
    void shouldOpenSessionForFutureBusinessDate() {
        UUID ownerId = UUID.randomUUID();
        Queue queue = createQueue(ownerId);
        LocalDate futureDate = SESSION_DATE.plusDays(1);
        when(queueRepository.findByIdForUpdate(queue.getId()))
                .thenReturn(Optional.of(queue));
        when(queueSessionRepository.findByQueueIdAndSessionDate(queue.getId(), futureDate))
                .thenReturn(Optional.empty());

        QueueSession opened = queueSessionService.openSession(
                queue.getId(),
                ownerId,
                futureDate,
                futureDate.atTime(9, 0).atZone(BUSINESS_ZONE).toInstant()
        );

        assertThat(opened.getSessionDate()).isEqualTo(futureDate);
        assertThat(opened.getStatus()).isEqualTo(QueueSessionStatus.OPEN);
    }

    @Test
    void shouldPauseAndResumeSession() {
        UUID ownerId = UUID.randomUUID();
        Queue queue = createQueue(ownerId);
        QueueSession session = createSession(queue, QueueSessionStatus.OPEN);
        List<QueueEntry> activeEntries = List.of(
                createEntry(QueueEntryStatus.WAITING, 3),
                createEntry(QueueEntryStatus.CALLED, 7)
        );
        when(queueSessionRepository.findByIdForUpdate(session.getId()))
                .thenReturn(Optional.of(session));

        QueueSession paused = queueSessionService.pauseSession(session.getId(), ownerId);
        assertThat(paused.getStatus()).isEqualTo(QueueSessionStatus.PAUSED);

        QueueSession resumed = queueSessionService.resumeSession(session.getId(), ownerId);
        assertThat(resumed.getStatus()).isEqualTo(QueueSessionStatus.OPEN);
        assertThat(queueSessionService.resumeSession(session.getId(), ownerId))
                .isSameAs(resumed);
        assertThat(activeEntries)
                .extracting(QueueEntry::getStatus)
                .containsExactly(QueueEntryStatus.WAITING, QueueEntryStatus.CALLED);
        assertThat(activeEntries)
                .extracting(QueueEntry::getTicketNumber)
                .containsExactly(3, 7);
        verify(sessionOperationRepository).save(
                org.mockito.ArgumentMatchers.argThat(operation ->
                        operation.getOperationType() == SessionOperationType.PAUSED)
        );
        verify(sessionOperationRepository).save(
                org.mockito.ArgumentMatchers.argThat(operation ->
                        operation.getOperationType() == SessionOperationType.RESUMED)
        );
        verify(outboxEventRepository, org.mockito.Mockito.times(2))
                .save(any(OutboxEvent.class));
    }

    @Test
    void shouldNotCreateDuplicateAuditWhenPauseIsRepeated() {
        UUID ownerId = UUID.randomUUID();
        QueueSession session = createSession(createQueue(ownerId), QueueSessionStatus.OPEN);
        when(queueSessionRepository.findByIdForUpdate(session.getId()))
                .thenReturn(Optional.of(session));

        queueSessionService.pauseSession(session.getId(), ownerId);
        queueSessionService.pauseSession(session.getId(), ownerId);

        verify(sessionOperationRepository).save(any(SessionOperation.class));
        verify(sessionOperationRepository, org.mockito.Mockito.times(1))
                .save(any(SessionOperation.class));
        verify(outboxEventRepository, org.mockito.Mockito.times(1))
                .save(any(OutboxEvent.class));
    }

    @Test
    void shouldRejectInvalidPauseAndResumeTransitions() {
        UUID ownerId = UUID.randomUUID();
        Queue queue = createQueue(ownerId);
        QueueSession closed = createSession(queue, QueueSessionStatus.CLOSED);
        when(queueSessionRepository.findByIdForUpdate(closed.getId()))
                .thenReturn(Optional.of(closed));

        assertThatThrownBy(() -> queueSessionService.pauseSession(closed.getId(), ownerId))
                .isInstanceOf(QueueValidationException.class);
        assertThatThrownBy(() -> queueSessionService.resumeSession(closed.getId(), ownerId))
                .isInstanceOf(QueueValidationException.class);
        verifyNoInteractions(sessionOperationRepository, outboxEventRepository);
    }

    @Test
    void shouldPreventClosingWhenActiveEntriesRemain() {
        UUID ownerId = UUID.randomUUID();
        QueueSession session = createSession(createQueue(ownerId), QueueSessionStatus.OPEN);
        when(queueSessionRepository.findByIdForUpdate(session.getId()))
                .thenReturn(Optional.of(session));
        when(queueEntryRepository.countBySessionIdAndStatusIn(
                session.getId(), List.of(QueueEntryStatus.WAITING, QueueEntryStatus.CALLED)
        )).thenReturn(1L);

        assertThatThrownBy(() -> queueSessionService.closeSession(session.getId(), ownerId))
                .isInstanceOf(QueueValidationException.class)
                .hasMessageContaining("WAITING or CALLED");

        verifyNoInteractions(sessionOperationRepository, outboxEventRepository);
    }

    @Test
    void shouldCloseSessionWhenNoActiveEntriesRemain() {
        UUID ownerId = UUID.randomUUID();
        QueueSession session = createSession(createQueue(ownerId), QueueSessionStatus.PAUSED);
        when(queueSessionRepository.findByIdForUpdate(session.getId()))
                .thenReturn(Optional.of(session));
        when(queueEntryRepository.countBySessionIdAndStatusIn(
                session.getId(), List.of(QueueEntryStatus.WAITING, QueueEntryStatus.CALLED)
        )).thenReturn(0L);

        QueueSession closed = queueSessionService.closeSession(session.getId(), ownerId);

        assertThat(closed.getStatus()).isEqualTo(QueueSessionStatus.CLOSED);
        assertThat(closed.getClosedAt()).isNotNull();
        verify(sessionOperationRepository).save(any(SessionOperation.class));
    }

    @Test
    void shouldNotDuplicateAuditWhenCloseIsRepeated() {
        UUID ownerId = UUID.randomUUID();
        QueueSession session = createSession(createQueue(ownerId), QueueSessionStatus.OPEN);
        when(queueSessionRepository.findByIdForUpdate(session.getId()))
                .thenReturn(Optional.of(session));

        queueSessionService.closeSession(session.getId(), ownerId);
        queueSessionService.closeSession(session.getId(), ownerId);

        verify(queueEntryRepository).countBySessionIdAndStatusIn(
                session.getId(), List.of(QueueEntryStatus.WAITING, QueueEntryStatus.CALLED)
        );
        verify(sessionOperationRepository).save(any(SessionOperation.class));
        verify(sessionOperationRepository, org.mockito.Mockito.times(1))
                .save(any(SessionOperation.class));
    }

    @Test
    void shouldCancelSessionAndOnlyCancelWaitingAndCalledEntries() {
        UUID ownerId = UUID.randomUUID();
        QueueSession session = createSession(createQueue(ownerId), QueueSessionStatus.OPEN);
        List<QueueEntry> entries = List.of(
                createEntry(QueueEntryStatus.WAITING),
                createEntry(QueueEntryStatus.CALLED),
                createEntry(QueueEntryStatus.COMPLETED),
                createEntry(QueueEntryStatus.SKIPPED),
                createEntry(QueueEntryStatus.LEFT)
        );
        when(queueSessionRepository.findByIdForUpdate(session.getId()))
                .thenReturn(Optional.of(session));
        when(queueEntryRepository.findBySessionIdAndStatusIn(
                session.getId(), List.of(QueueEntryStatus.WAITING, QueueEntryStatus.CALLED)
        )).thenReturn(entries.subList(0, 2));

        QueueSession cancelled = queueSessionService.cancelSession(
                session.getId(), ownerId, "  Unexpected closure  "
        );

        assertThat(cancelled.getStatus()).isEqualTo(QueueSessionStatus.CANCELLED);
        assertThat(cancelled.getCancellationReason()).isEqualTo("Unexpected closure");
        assertThat(cancelled.getClosedAt()).isNotNull();
        assertThat(entries)
                .extracting(QueueEntry::getStatus)
                .containsExactly(
                        QueueEntryStatus.CANCELLED,
                        QueueEntryStatus.CANCELLED,
                        QueueEntryStatus.COMPLETED,
                        QueueEntryStatus.SKIPPED,
                        QueueEntryStatus.LEFT
                );
        verify(queueEntryRepository).saveAll(entries.subList(0, 2));
        verify(queueOperationRepository).saveAll(
                org.mockito.ArgumentMatchers.argThat(savedOperations -> {
                    List<QueueOperation> operations = java.util.stream.StreamSupport
                            .stream(savedOperations.spliterator(), false)
                            .toList();
                    assertThat(operations).hasSize(2);
                    assertThat(operations)
                            .extracting(operation -> operation.getQueueEntry().getId())
                            .containsExactlyInAnyOrder(
                                    entries.get(0).getId(),
                                    entries.get(1).getId()
                            );
                    assertThat(operations)
                            .extracting(QueueOperation::getOperationType)
                            .containsOnly(QueueOperationType.CANCELLED);
                    assertThat(operations)
                            .extracting(QueueOperation::getPerformedByUserId)
                            .containsOnly(ownerId);
                    assertThat(operations)
                            .allSatisfy(operation -> assertThat(
                                            operation.getDetails()
                                                    .get("cancellationReason")
                                                    .asText()
                                    )
                                    .isEqualTo("Unexpected closure"));
                    return true;
                })
        );
        verify(sessionOperationRepository).save(any(SessionOperation.class));
        verify(outboxEventRepository).save(any(OutboxEvent.class));
    }

    @Test
    void shouldAllowCancellationAfterScheduledStart() {
        UUID ownerId = UUID.randomUUID();
        QueueSession session = createSession(createQueue(ownerId), QueueSessionStatus.PAUSED);
        session.setScheduledStartAt(Instant.parse("2020-01-01T00:00:00Z"));
        when(queueSessionRepository.findByIdForUpdate(session.getId()))
                .thenReturn(Optional.of(session));
        when(queueEntryRepository.findBySessionIdAndStatusIn(
                session.getId(), List.of(QueueEntryStatus.WAITING, QueueEntryStatus.CALLED)
        )).thenReturn(List.of());

        assertThat(queueSessionService.cancelSession(
                session.getId(), ownerId, "Cancelled after start"
        ).getStatus()).isEqualTo(QueueSessionStatus.CANCELLED);
    }

    @Test
    void shouldNotDuplicateAuditWhenCancellationIsRepeated() {
        UUID ownerId = UUID.randomUUID();
        QueueSession session = createSession(createQueue(ownerId), QueueSessionStatus.OPEN);
        List<QueueEntry> activeEntries = List.of(
                createEntry(QueueEntryStatus.WAITING),
                createEntry(QueueEntryStatus.CALLED)
        );
        when(queueSessionRepository.findByIdForUpdate(session.getId()))
                .thenReturn(Optional.of(session));
        when(queueEntryRepository.findBySessionIdAndStatusIn(
                session.getId(), List.of(QueueEntryStatus.WAITING, QueueEntryStatus.CALLED)
        )).thenReturn(activeEntries);

        queueSessionService.cancelSession(session.getId(), ownerId, "Reason");
        queueSessionService.cancelSession(session.getId(), ownerId, "Different reason");

        verify(sessionOperationRepository).save(any(SessionOperation.class));
        verify(sessionOperationRepository, org.mockito.Mockito.times(1))
                .save(any(SessionOperation.class));
        verify(outboxEventRepository, org.mockito.Mockito.times(1))
                .save(any(OutboxEvent.class));
        verify(queueOperationRepository, org.mockito.Mockito.times(1))
                .saveAll(any());
    }

    @Test
    void shouldRejectManagementByAnotherBusinessOwner() {
        UUID ownerId = UUID.randomUUID();
        QueueSession session = createSession(createQueue(ownerId), QueueSessionStatus.OPEN);
        when(queueSessionRepository.findByIdForUpdate(session.getId()))
                .thenReturn(Optional.of(session));

        assertThatThrownBy(() -> queueSessionService.pauseSession(
                session.getId(), UUID.randomUUID()
        )).isInstanceOf(BusinessAccessDeniedException.class);

        verifyNoInteractions(sessionOperationRepository, outboxEventRepository);
    }

    private Queue createQueue(UUID ownerId) {
        Business business = BeanUtils.instantiateClass(Business.class);
        business.setId(UUID.randomUUID());
        business.setOwnerUserId(ownerId);
        business.setName("Test Business");
        business.setTimezone("Asia/Colombo");

        Queue queue = BeanUtils.instantiateClass(Queue.class);
        queue.setId(UUID.randomUUID());
        queue.setBusiness(business);
        queue.setName("General");
        queue.setMaxCapacity(50);
        queue.setAverageServiceTimeMinutes(10);
        queue.setActive(true);
        return queue;
    }

    private QueueSession createSession(Queue queue, QueueSessionStatus status) {
        QueueSession session = BeanUtils.instantiateClass(QueueSession.class);
        session.setId(UUID.randomUUID());
        session.setQueue(queue);
        session.setSessionDate(SESSION_DATE);
        session.setScheduledStartAt(SESSION_DATE.atStartOfDay(BUSINESS_ZONE).toInstant());
        session.setCapacity(queue.getMaxCapacity());
        session.setStatus(status);
        session.setNextTicketNumber(1);
        return session;
    }

    private QueueEntry createEntry(QueueEntryStatus status) {
        QueueEntry entry = BeanUtils.instantiateClass(QueueEntry.class);
        entry.setId(UUID.randomUUID());
        entry.setStatus(status);
        return entry;
    }

    private QueueEntry createEntry(QueueEntryStatus status, int ticketNumber) {
        QueueEntry entry = createEntry(status);
        entry.setTicketNumber(ticketNumber);
        entry.setTicketCode("T" + ticketNumber);
        return entry;
    }
}
