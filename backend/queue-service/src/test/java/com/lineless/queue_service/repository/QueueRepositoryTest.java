package com.lineless.queue_service.repository;

import com.lineless.queue_service.config.AbstractRepositoryTest;
import com.lineless.queue_service.entity.Business;
import com.lineless.queue_service.entity.Queue;

import org.junit.jupiter.api.Test;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class QueueRepositoryTest extends AbstractRepositoryTest {

    @Autowired
    private BusinessRepository businessRepository;

    @Autowired
    private QueueRepository queueRepository;

    @Test
    void shouldFindQueuesByBusinessId() {

        Business business = createBusiness();

        Queue firstQueue = createQueue(business, "General");
        Queue secondQueue = createQueue(business, "Appointments");

        Business anotherBusiness = createBusiness();
        createQueue(anotherBusiness, "Other Queue");

        List<Queue> queues =
                queueRepository.findByBusinessId(business.getId());

        assertThat(queues)
                .extracting(Queue::getId)
                .containsExactlyInAnyOrder(
                        firstQueue.getId(),
                        secondQueue.getId()
                );
    }

    private Business createBusiness() {
        Business business = BeanUtils.instantiateClass(Business.class);

        business.setOwnerUserId(UUID.randomUUID());
        business.setName("Test Business");
        business.setPublic(true);

        return businessRepository.saveAndFlush(business);
    }


    private Queue createQueue(Business business, String name) {
        Queue queue = BeanUtils.instantiateClass(Queue.class);

        queue.setBusiness(business);
        queue.setName(name);
        queue.setAverageServiceTimeMinutes(10);
        queue.setMaxCapacity(50);

        return queueRepository.saveAndFlush(queue);
    }
}
