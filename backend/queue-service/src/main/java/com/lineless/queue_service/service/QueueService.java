package com.lineless.queue_service.service;

import com.lineless.queue_service.entity.Business;
import com.lineless.queue_service.entity.Queue;
import com.lineless.queue_service.exception.BusinessAccessDeniedException;
import com.lineless.queue_service.exception.BusinessNotFoundException;
import com.lineless.queue_service.exception.DuplicateQueueNameException;
import com.lineless.queue_service.exception.QueueAccessDeniedException;
import com.lineless.queue_service.exception.QueueInUseException;
import com.lineless.queue_service.exception.QueueNotFoundException;
import com.lineless.queue_service.exception.QueueValidationException;
import com.lineless.queue_service.repository.BusinessRepository;
import com.lineless.queue_service.repository.QueueRepository;
import com.lineless.queue_service.repository.QueueSessionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class QueueService {

    private final QueueRepository queueRepository;
    private final BusinessRepository businessRepository;
    private final QueueSessionRepository queueSessionRepository;

    @Transactional
    public Queue createQueue(
            UUID businessId,
            UUID actingOwnerUserId,
            String name,
            String description,
            String ticketPrefix,
            int maxCapacity,
            int averageServiceTimeMinutes,
            boolean isActive
    ) {
        validateOwner(actingOwnerUserId);
        validateQueueDetails(name, ticketPrefix, maxCapacity, averageServiceTimeMinutes);

        Business business = findOwnedBusiness(businessId, actingOwnerUserId);
        String normalizedName = name.trim();

        if (queueRepository.existsByBusinessIdAndName(businessId, normalizedName)) {
            throw new DuplicateQueueNameException(normalizedName);
        }

        Queue queue = BeanUtils.instantiateClass(Queue.class);
        queue.setBusiness(business);
        queue.setName(normalizedName);
        queue.setDescription(normalizeOptional(description));
        queue.setTicketPrefix(normalizeOptional(ticketPrefix));
        queue.setMaxCapacity(maxCapacity);
        queue.setAverageServiceTimeMinutes(averageServiceTimeMinutes);
        queue.setActive(isActive);

        return queueRepository.save(queue);
    }

    @Transactional
    public Queue updateQueue(
            UUID queueId,
            UUID actingOwnerUserId,
            String name,
            String description,
            String ticketPrefix,
            int maxCapacity,
            int averageServiceTimeMinutes,
            boolean isActive
    ) {
        validateOwner(actingOwnerUserId);
        validateQueueDetails(name, ticketPrefix, maxCapacity, averageServiceTimeMinutes);

        Queue queue = findManagedQueueForUpdate(queueId, actingOwnerUserId);
        String normalizedName = name.trim();
        UUID businessId = queue.getBusiness().getId();

        if (queueRepository.existsByBusinessIdAndNameAndIdNot(
                businessId,
                normalizedName,
                queueId
        )) {
            throw new DuplicateQueueNameException(normalizedName);
        }

        queue.setName(normalizedName);
        queue.setDescription(normalizeOptional(description));
        queue.setTicketPrefix(normalizeOptional(ticketPrefix));
        queue.setMaxCapacity(maxCapacity);
        queue.setAverageServiceTimeMinutes(averageServiceTimeMinutes);
        queue.setActive(isActive);

        return queueRepository.save(queue);
    }

    @Transactional(readOnly = true)
    public Queue getQueueById(UUID queueId) {
        return queueRepository.findById(requireId(queueId, "queue ID"))
                .orElseThrow(() -> new QueueNotFoundException(queueId));
    }

    @Transactional(readOnly = true)
    public Queue getQueueById(UUID queueId, UUID actingOwnerUserId) {
        return findManagedQueue(queueId, actingOwnerUserId);
    }

    @Transactional(readOnly = true)
    public List<Queue> getQueuesByBusiness(UUID businessId, UUID actingOwnerUserId) {
        validateOwner(actingOwnerUserId);
        findOwnedBusiness(businessId, actingOwnerUserId);
        return queueRepository.findByBusinessId(businessId);
    }

    @Transactional(readOnly = true)
    public List<Queue> getActiveQueuesByBusiness(UUID businessId, UUID actingOwnerUserId) {
        validateOwner(actingOwnerUserId);
        findOwnedBusiness(businessId, actingOwnerUserId);
        return queueRepository.findByBusinessIdAndIsActiveTrue(businessId);
    }

    @Transactional
    public Queue activateQueue(UUID queueId, UUID actingOwnerUserId) {
        Queue queue = findManagedQueueForUpdate(queueId, actingOwnerUserId);
        queue.setActive(true);
        return queueRepository.save(queue);
    }

    @Transactional
    public Queue deactivateQueue(UUID queueId, UUID actingOwnerUserId) {
        Queue queue = findManagedQueueForUpdate(queueId, actingOwnerUserId);
        queue.setActive(false);
        return queueRepository.save(queue);
    }

    @Transactional
    public void deleteQueue(UUID queueId, UUID actingOwnerUserId) {
        Queue queue = findManagedQueueForUpdate(queueId, actingOwnerUserId);

        if (queueSessionRepository.existsByQueueId(queueId)) {
            throw new QueueInUseException(queueId);
        }

        queueRepository.delete(queue);
    }

    private Queue findManagedQueue(UUID queueId, UUID actingOwnerUserId) {
        validateOwner(actingOwnerUserId);
        Queue queue = queueRepository.findById(requireId(queueId, "queue ID"))
                .orElseThrow(() -> new QueueNotFoundException(queueId));

        Business business = queue.getBusiness();
        if (!business.getOwnerUserId().equals(actingOwnerUserId)) {
            throw new QueueAccessDeniedException(queueId);
        }

        return queue;
    }

    private Queue findManagedQueueForUpdate(UUID queueId, UUID actingOwnerUserId) {
        validateOwner(actingOwnerUserId);
        Queue queue = queueRepository.findByIdForUpdate(
                        requireId(queueId, "queue ID")
                )
                .orElseThrow(() -> new QueueNotFoundException(queueId));

        Business business = queue.getBusiness();
        if (!business.getOwnerUserId().equals(actingOwnerUserId)) {
            throw new QueueAccessDeniedException(queueId);
        }

        return queue;
    }

    private Business findOwnedBusiness(UUID businessId, UUID actingOwnerUserId) {
        Business business = businessRepository.findById(requireId(businessId, "business ID"))
                .orElseThrow(() -> new BusinessNotFoundException(businessId));

        if (!business.getOwnerUserId().equals(actingOwnerUserId)) {
            throw new BusinessAccessDeniedException(businessId);
        }

        return business;
    }

    private void validateOwner(UUID ownerUserId) {
        if (ownerUserId == null) {
            throw new QueueValidationException("owner user ID is required");
        }
    }

    private UUID requireId(UUID id, String fieldName) {
        if (id == null) {
            throw new QueueValidationException(fieldName + " is required");
        }
        return id;
    }

    private void validateQueueDetails(
            String name,
            String ticketPrefix,
            int maxCapacity,
            int averageServiceTimeMinutes
    ) {
        if (name == null || name.isBlank()) {
            throw new QueueValidationException("Queue name is required");
        }
        if (name.trim().length() > 255) {
            throw new QueueValidationException("Queue name must be at most 255 characters");
        }
        if (ticketPrefix != null
                && !ticketPrefix.isBlank()
                && ticketPrefix.trim().length() > 10) {
            throw new QueueValidationException("Ticket prefix must be at most 10 characters");
        }
        if (maxCapacity <= 0) {
            throw new QueueValidationException("Maximum capacity must be positive");
        }
        if (averageServiceTimeMinutes <= 0) {
            throw new QueueValidationException("Average service time must be positive");
        }
    }

    private String normalizeOptional(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }
}
