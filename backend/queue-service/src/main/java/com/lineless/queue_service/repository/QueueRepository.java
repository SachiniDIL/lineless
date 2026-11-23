package com.lineless.queue_service.repository;

import com.lineless.queue_service.entity.Queue;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface QueueRepository extends JpaRepository<Queue, UUID> {

    List<Queue> findByBusinessId(UUID businessId);

    List<Queue> findByBusinessIdAndIsActiveTrue(UUID businessId);

    boolean existsByBusinessId(UUID businessId);
}
