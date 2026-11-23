package com.lineless.queue_service.service;

import com.lineless.queue_service.entity.Business;
import com.lineless.queue_service.entity.Queue;
import com.lineless.queue_service.exception.BusinessAccessDeniedException;
import com.lineless.queue_service.exception.DuplicateQueueNameException;
import com.lineless.queue_service.exception.QueueAccessDeniedException;
import com.lineless.queue_service.exception.QueueInUseException;
import com.lineless.queue_service.exception.QueueNotFoundException;
import com.lineless.queue_service.exception.QueueValidationException;
import com.lineless.queue_service.repository.BusinessRepository;
import com.lineless.queue_service.repository.QueueRepository;
import com.lineless.queue_service.repository.QueueSessionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.BeanUtils;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class QueueServiceTest {

    @Mock
    private QueueRepository queueRepository;

    @Mock
    private BusinessRepository businessRepository;

    @Mock
    private QueueSessionRepository queueSessionRepository;

    private QueueService queueService;

    @BeforeEach
    void setUp() {
        queueService = new QueueService(
                queueRepository,
                businessRepository,
                queueSessionRepository
        );
    }

    @Test
    void shouldCreateQueue() {
        UUID ownerId = UUID.randomUUID();
        UUID businessId = UUID.randomUUID();
        Business business = createBusiness(ownerId, businessId);

        when(businessRepository.findById(businessId))
                .thenReturn(Optional.of(business));
        when(queueRepository.existsByBusinessIdAndName(businessId, "Main Queue"))
                .thenReturn(false);
        when(queueRepository.save(any(Queue.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        Queue created = queueService.createQueue(
                businessId,
                ownerId,
                "  Main Queue  ",
                "Queue description",
                "  MQ ",
                50,
                10,
                true
        );

        assertThat(created.getBusiness()).isSameAs(business);
        assertThat(created.getName()).isEqualTo("Main Queue");
        assertThat(created.getTicketPrefix()).isEqualTo("MQ");
        assertThat(created.getMaxCapacity()).isEqualTo(50);
        assertThat(created.getAverageServiceTimeMinutes()).isEqualTo(10);
        assertThat(created.isActive()).isTrue();
    }

    @Test
    void shouldUpdateQueueDetails() {
        UUID ownerId = UUID.randomUUID();
        UUID businessId = UUID.randomUUID();
        UUID queueId = UUID.randomUUID();
        Business business = createBusiness(ownerId, businessId);
        Queue queue = createQueue(ownerId, business, "Old Queue");
        queue.setId(queueId);

        when(queueRepository.findById(queueId))
                .thenReturn(Optional.of(queue));
        when(queueRepository.existsByBusinessIdAndNameAndIdNot(
                businessId,
                "New Queue",
                queueId
        )).thenReturn(false);
        when(queueRepository.save(any(Queue.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        Queue updated = queueService.updateQueue(
                queueId,
                ownerId,
                "New Queue",
                "Updated",
                "NEW",
                100,
                15,
                false
        );

        assertThat(updated.getName()).isEqualTo("New Queue");
        assertThat(updated.getDescription()).isEqualTo("Updated");
        assertThat(updated.getTicketPrefix()).isEqualTo("NEW");
        assertThat(updated.getMaxCapacity()).isEqualTo(100);
        assertThat(updated.getAverageServiceTimeMinutes()).isEqualTo(15);
        assertThat(updated.isActive()).isFalse();
    }

    @Test
    void shouldRejectManagementByAnotherBusinessOwner() {
        UUID ownerId = UUID.randomUUID();
        UUID otherOwnerId = UUID.randomUUID();
        UUID businessId = UUID.randomUUID();
        UUID queueId = UUID.randomUUID();
        Business business = createBusiness(ownerId, businessId);
        Queue queue = createQueue(ownerId, business, "Queue");
        queue.setId(queueId);

        when(queueRepository.findById(queueId))
                .thenReturn(Optional.of(queue));

        assertThatThrownBy(() ->
                queueService.updateQueue(
                        queueId,
                        otherOwnerId,
                        "Changed",
                        null,
                        null,
                        10,
                        5,
                        true
                ))
                .isInstanceOf(QueueAccessDeniedException.class);

        verify(queueRepository, never()).save(any());
    }

    @Test
    void shouldRejectDuplicateQueueNames() {
        UUID ownerId = UUID.randomUUID();
        UUID businessId = UUID.randomUUID();
        UUID queueId = UUID.randomUUID();
        Business business = createBusiness(ownerId, businessId);
        Queue existing = createQueue(ownerId, business, "Main");
        existing.setId(queueId);

        when(queueRepository.findById(queueId))
                .thenReturn(Optional.of(existing));
        when(queueRepository.existsByBusinessIdAndNameAndIdNot(
                businessId,
                "Duplicate",
                queueId
        )).thenReturn(true);

        assertThatThrownBy(() ->
                queueService.updateQueue(
                        queueId,
                        ownerId,
                        "Duplicate",
                        null,
                        null,
                        10,
                        5,
                        true
                ))
                .isInstanceOf(DuplicateQueueNameException.class);

        verify(queueRepository, never()).save(any());
    }

    @Test
    void shouldRejectInvalidCapacityAndServiceTime() {
        UUID ownerId = UUID.randomUUID();
        UUID businessId = UUID.randomUUID();

        assertThatThrownBy(() ->
                queueService.createQueue(
                        businessId,
                        ownerId,
                        "Queue",
                        null,
                        null,
                        0,
                        10,
                        true
                ))
                .isInstanceOf(QueueValidationException.class)
                .hasMessageContaining("Maximum capacity");

        assertThatThrownBy(() ->
                queueService.createQueue(
                        businessId,
                        ownerId,
                        "Queue",
                        null,
                        null,
                        10,
                        -1,
                        true
                ))
                .isInstanceOf(QueueValidationException.class)
                .hasMessageContaining("Average service time");

        verify(queueRepository, never()).save(any());
    }

    @Test
    void shouldListQueuesByBusiness() {
        UUID ownerId = UUID.randomUUID();
        UUID businessId = UUID.randomUUID();
        Business business = createBusiness(ownerId, businessId);
        Queue active = createQueue(ownerId, business, "Active");
        Queue inactive = createQueue(ownerId, business, "Inactive");
        inactive.setActive(false);

        when(businessRepository.findById(businessId))
                .thenReturn(Optional.of(business));
        when(queueRepository.findByBusinessId(businessId))
                .thenReturn(List.of(active, inactive));
        when(queueRepository.findByBusinessIdAndIsActiveTrue(businessId))
                .thenReturn(List.of(active));

        assertThat(queueService.getQueuesByBusiness(businessId, ownerId))
                .containsExactly(active, inactive);
        assertThat(queueService.getActiveQueuesByBusiness(businessId, ownerId))
                .containsExactly(active);
    }

    @Test
    void shouldActivateAndDeactivateQueue() {
        UUID ownerId = UUID.randomUUID();
        UUID businessId = UUID.randomUUID();
        UUID queueId = UUID.randomUUID();
        Business business = createBusiness(ownerId, businessId);
        Queue queue = createQueue(ownerId, business, "Queue");
        queue.setId(queueId);
        queue.setActive(false);

        when(queueRepository.findById(queueId))
                .thenReturn(Optional.of(queue));
        when(queueRepository.save(queue))
                .thenReturn(queue);

        Queue activated = queueService.activateQueue(queueId, ownerId);
        assertThat(activated.isActive()).isTrue();

        Queue deactivated = queueService.deactivateQueue(queueId, ownerId);
        assertThat(deactivated.isActive()).isFalse();
    }

    @Test
    void shouldRejectDeletionWhenSessionsExist() {
        UUID ownerId = UUID.randomUUID();
        UUID businessId = UUID.randomUUID();
        UUID queueId = UUID.randomUUID();
        Business business = createBusiness(ownerId, businessId);
        Queue queue = createQueue(ownerId, business, "Queue");
        queue.setId(queueId);

        when(queueRepository.findById(queueId))
                .thenReturn(Optional.of(queue));
        when(queueSessionRepository.existsByQueueId(queueId))
                .thenReturn(true);

        assertThatThrownBy(() ->
                queueService.deleteQueue(queueId, ownerId))
                .isInstanceOf(QueueInUseException.class);

        verify(queueRepository, never()).delete(any());
    }

    @Test
    void shouldSuccessfullyDeleteQueueWithoutSessions() {
        UUID ownerId = UUID.randomUUID();
        UUID businessId = UUID.randomUUID();
        UUID queueId = UUID.randomUUID();
        Business business = createBusiness(ownerId, businessId);
        Queue queue = createQueue(ownerId, business, "Queue");
        queue.setId(queueId);

        when(queueRepository.findById(queueId))
                .thenReturn(Optional.of(queue));
        when(queueSessionRepository.existsByQueueId(queueId))
                .thenReturn(false);

        queueService.deleteQueue(queueId, ownerId);

        verify(queueRepository).delete(queue);
    }

    @Test
    void shouldFindQueueByIdAndRejectUnknownQueue() {
        UUID queueId = UUID.randomUUID();
        Queue queue = createQueue(
                UUID.randomUUID(),
                createBusiness(UUID.randomUUID(), UUID.randomUUID()),
                "Queue"
        );
        queue.setId(queueId);

        when(queueRepository.findById(queueId))
                .thenReturn(Optional.of(queue));

        assertThat(queueService.getQueueById(queueId)).isSameAs(queue);

        UUID unknownId = UUID.randomUUID();
        when(queueRepository.findById(unknownId))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> queueService.getQueueById(unknownId))
                .isInstanceOf(QueueNotFoundException.class);
    }

    private Business createBusiness(UUID ownerId, UUID businessId) {
        Business business = BeanUtils.instantiateClass(Business.class);
        business.setId(businessId);
        business.setOwnerUserId(ownerId);
        business.setName("Business");
        business.setTimezone("Asia/Colombo");
        business.setPublic(true);
        return business;
    }

    private Queue createQueue(UUID ownerId, Business business, String name) {
        Queue queue = BeanUtils.instantiateClass(Queue.class);
        queue.setBusiness(business);
        queue.setName(name);
        queue.setMaxCapacity(50);
        queue.setAverageServiceTimeMinutes(10);
        queue.setActive(true);
        return queue;
    }
}
