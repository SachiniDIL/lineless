package com.lineless.queue_service.service;

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
import com.lineless.queue_service.exception.QueueNotFoundException;
import com.lineless.queue_service.exception.QueueValidationException;
import com.lineless.queue_service.repository.OutboxEventRepository;
import com.lineless.queue_service.repository.QueueEntryRepository;
import com.lineless.queue_service.repository.QueueOperationRepository;
import com.lineless.queue_service.repository.QueueRepository;
import com.lineless.queue_service.repository.QueueSessionRepository;
import com.lineless.queue_service.repository.SessionOperationRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.node.JsonNodeFactory;
import tools.jackson.databind.node.ObjectNode;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.zone.ZoneRulesException;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class QueueSessionService {

    private static final List<QueueEntryStatus> ACTIVE_ENTRY_STATUSES = List.of(
            QueueEntryStatus.WAITING,
            QueueEntryStatus.CALLED
    );

    private final QueueSessionRepository queueSessionRepository;
    private final QueueRepository queueRepository;
    private final QueueEntryRepository queueEntryRepository;
    private final QueueOperationRepository queueOperationRepository;
    private final SessionOperationRepository sessionOperationRepository;
    private final OutboxEventRepository outboxEventRepository;

    @Transactional
    public QueueSession openSession(
            UUID queueId,
            UUID actingOwnerUserId,
            LocalDate sessionDate,
            Instant scheduledStartAt
    ) {
        validateOwner(actingOwnerUserId);
        requireId(queueId, "queue ID");
        validateSessionSchedule(sessionDate, scheduledStartAt);

        Queue queue = queueRepository.findByIdForUpdate(queueId)
                .orElseThrow(() -> new QueueNotFoundException(queueId));
        validateOwnership(queue, actingOwnerUserId);

        ZoneId businessZone = resolveBusinessZone(queue);
        if (!scheduledStartAt.atZone(businessZone).toLocalDate().equals(sessionDate)) {
            throw new QueueValidationException(
                    "Scheduled start time must be on the session's business date"
            );
        }

        QueueSession existingSession = queueSessionRepository
                .findByQueueIdAndSessionDate(queueId, sessionDate)
                .orElse(null);
        if (existingSession != null) {
            if (existingSession.getStatus() == QueueSessionStatus.OPEN) {
                return existingSession;
            }
            throw new QueueValidationException(
                    "A session already exists for this queue and date in status "
                            + existingSession.getStatus()
            );
        }

        if (sessionDate.isBefore(LocalDate.now(businessZone))) {
            throw new QueueValidationException("Cannot open a session for a past business date");
        }
        if (!queue.isActive()) {
            throw new QueueValidationException("Cannot open a session for an inactive queue");
        }

        return createOpenSession(queue, sessionDate, scheduledStartAt, actingOwnerUserId);
    }

    @Transactional
    public QueueSession pauseSession(UUID sessionId, UUID actingOwnerUserId) {
        QueueSession session = findOwnedSessionForUpdate(sessionId, actingOwnerUserId);
        if (session.getStatus() == QueueSessionStatus.PAUSED) {
            return session;
        }
        requireStatus(session, QueueSessionStatus.OPEN, "pause");

        session.setStatus(QueueSessionStatus.PAUSED);
        return saveTransition(session, actingOwnerUserId, SessionOperationType.PAUSED);
    }

    @Transactional
    public QueueSession resumeSession(UUID sessionId, UUID actingOwnerUserId) {
        QueueSession session = findOwnedSessionForUpdate(sessionId, actingOwnerUserId);
        if (session.getStatus() == QueueSessionStatus.OPEN) {
            return session;
        }
        requireStatus(session, QueueSessionStatus.PAUSED, "resume");

        session.setStatus(QueueSessionStatus.OPEN);
        return saveTransition(session, actingOwnerUserId, SessionOperationType.RESUMED);
    }

    @Transactional
    public QueueSession closeSession(UUID sessionId, UUID actingOwnerUserId) {
        QueueSession session = findOwnedSessionForUpdate(sessionId, actingOwnerUserId);
        if (session.getStatus() == QueueSessionStatus.CLOSED) {
            return session;
        }
        requireOpenOrPaused(session, "close");

        long activeEntries = queueEntryRepository.countBySessionIdAndStatusIn(
                session.getId(),
                ACTIVE_ENTRY_STATUSES
        );
        if (activeEntries > 0) {
            throw new QueueValidationException(
                    "Cannot close a session while WAITING or CALLED entries remain"
            );
        }

        session.setStatus(QueueSessionStatus.CLOSED);
        session.setClosedAt(Instant.now());
        return saveTransition(session, actingOwnerUserId, SessionOperationType.CLOSED);
    }

    @Transactional
    public QueueSession cancelSession(
            UUID sessionId,
            UUID actingOwnerUserId,
            String cancellationReason
    ) {
        QueueSession session = findOwnedSessionForUpdate(sessionId, actingOwnerUserId);
        if (session.getStatus() == QueueSessionStatus.CANCELLED) {
            return session;
        }
        requireOpenOrPaused(session, "cancel");
        if (cancellationReason == null || cancellationReason.isBlank()) {
            throw new QueueValidationException("Cancellation reason is required");
        }

        List<QueueEntry> activeEntries = queueEntryRepository
                .findBySessionIdAndStatusIn(session.getId(), ACTIVE_ENTRY_STATUSES);
        activeEntries.forEach(entry -> entry.setStatus(QueueEntryStatus.CANCELLED));
        queueEntryRepository.saveAll(activeEntries);

        String normalizedReason = cancellationReason.trim();
        List<QueueOperation> entryOperations = activeEntries.stream()
                .map(entry -> createCancellationOperation(
                        entry,
                        actingOwnerUserId,
                        normalizedReason
                ))
                .toList();
        queueOperationRepository.saveAll(entryOperations);

        session.setStatus(QueueSessionStatus.CANCELLED);
        session.setCancellationReason(normalizedReason);
        session.setClosedAt(Instant.now());
        return saveTransition(
                session,
                actingOwnerUserId,
                SessionOperationType.CANCELLED,
                normalizedReason
        );
    }

    private QueueOperation createCancellationOperation(
            QueueEntry entry,
            UUID actingOwnerUserId,
            String cancellationReason
    ) {
        QueueOperation operation = BeanUtils.instantiateClass(QueueOperation.class);
        operation.setQueueEntry(entry);
        operation.setOperationType(QueueOperationType.CANCELLED);
        operation.setPerformedByUserId(actingOwnerUserId);

        ObjectNode details = JsonNodeFactory.instance.objectNode();
        details.put("cancellationReason", cancellationReason);
        operation.setDetails(details);
        return operation;
    }

    private QueueSession createOpenSession(
            Queue queue,
            LocalDate sessionDate,
            Instant scheduledStartAt,
            UUID actingOwnerUserId
    ) {
        QueueSession session = BeanUtils.instantiateClass(QueueSession.class);
        session.setQueue(queue);
        session.setSessionDate(sessionDate);
        session.setScheduledStartAt(scheduledStartAt);
        session.setCapacity(queue.getMaxCapacity());
        session.setStatus(QueueSessionStatus.OPEN);
        session.setNextTicketNumber(1);
        session.setOpenedAt(Instant.now());

        QueueSession savedSession = queueSessionRepository.saveAndFlush(session);
        recordTransition(savedSession, actingOwnerUserId, SessionOperationType.OPENED, null);
        return savedSession;
    }

    private QueueSession saveTransition(
            QueueSession session,
            UUID actingOwnerUserId,
            SessionOperationType operationType
    ) {
        return saveTransition(session, actingOwnerUserId, operationType, null);
    }

    private QueueSession saveTransition(
            QueueSession session,
            UUID actingOwnerUserId,
            SessionOperationType operationType,
            String details
    ) {
        QueueSession savedSession = queueSessionRepository.save(session);
        recordTransition(savedSession, actingOwnerUserId, operationType, details);
        return savedSession;
    }

    private void recordTransition(
            QueueSession session,
            UUID actingOwnerUserId,
            SessionOperationType operationType,
            String details
    ) {
        SessionOperation operation = BeanUtils.instantiateClass(SessionOperation.class);
        operation.setSession(session);
        operation.setOperationType(operationType);
        operation.setPerformedByUserId(actingOwnerUserId);
        if (details != null) {
            ObjectNode operationDetails = JsonNodeFactory.instance.objectNode();
            operationDetails.put("cancellationReason", details);
            operation.setDetails(operationDetails);
        }
        sessionOperationRepository.save(operation);

        OutboxEvent event = BeanUtils.instantiateClass(OutboxEvent.class);
        event.setAggregateType("QueueSession");
        event.setAggregateId(session.getId());
        event.setEventType("QUEUE_SESSION_" + operationType.name());
        event.setPayload(createEventPayload(session, details));
        outboxEventRepository.save(event);
    }

    private ObjectNode createEventPayload(QueueSession session, String details) {
        ObjectNode payload = JsonNodeFactory.instance.objectNode();
        payload.put("sessionId", session.getId().toString());
        payload.put("queueId", session.getQueue().getId().toString());
        payload.put("sessionDate", session.getSessionDate().toString());
        payload.put("status", session.getStatus().name());
        payload.put("capacity", session.getCapacity());
        if (details != null) {
            payload.put("cancellationReason", details);
        }
        return payload;
    }

    private QueueSession findOwnedSessionForUpdate(UUID sessionId, UUID actingOwnerUserId) {
        validateOwner(actingOwnerUserId);
        requireId(sessionId, "session ID");

        QueueSession session = queueSessionRepository.findByIdForUpdate(sessionId)
                .orElseThrow(() -> new QueueValidationException("Queue session not found: " + sessionId));
        validateOwnership(session.getQueue(), actingOwnerUserId);
        return session;
    }

    private void validateOwnership(Queue queue, UUID actingOwnerUserId) {
        if (!queue.getBusiness().getOwnerUserId().equals(actingOwnerUserId)) {
            throw new BusinessAccessDeniedException(queue.getBusiness().getId());
        }
    }

    private void validateSessionSchedule(LocalDate sessionDate, Instant scheduledStartAt) {
        if (sessionDate == null) {
            throw new QueueValidationException("Session date is required");
        }
        if (scheduledStartAt == null) {
            throw new QueueValidationException("Scheduled start time is required");
        }
    }

    private ZoneId resolveBusinessZone(Queue queue) {
        try {
            return ZoneId.of(queue.getBusiness().getTimezone());
        } catch (ZoneRulesException | NullPointerException exception) {
            throw new QueueValidationException("Business timezone is invalid");
        }
    }

    private void requireOpenOrPaused(QueueSession session, String action) {
        if (session.getStatus() != QueueSessionStatus.OPEN
                && session.getStatus() != QueueSessionStatus.PAUSED) {
            throw new QueueValidationException(
                    "Cannot " + action + " a session in status " + session.getStatus()
            );
        }
    }

    private void requireStatus(
            QueueSession session,
            QueueSessionStatus requiredStatus,
            String action
    ) {
        if (session.getStatus() != requiredStatus) {
            throw new QueueValidationException(
                    "Cannot " + action + " a session in status " + session.getStatus()
            );
        }
    }

    private void validateOwner(UUID actingOwnerUserId) {
        requireId(actingOwnerUserId, "owner user ID");
    }

    private void requireId(UUID id, String fieldName) {
        if (id == null) {
            throw new QueueValidationException(fieldName + " is required");
        }
    }
}
