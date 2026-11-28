package com.lineless.queue_service.repository;

import com.lineless.queue_service.entity.Queue;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface QueueRepository extends JpaRepository<Queue, UUID> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select q from Queue q where q.id = :queueId")
    Optional<Queue> findByIdForUpdate(@Param("queueId") UUID queueId);

    List<Queue> findByBusinessId(UUID businessId);

    List<Queue> findByBusinessIdAndIsActiveTrue(UUID businessId);

    boolean existsByBusinessId(UUID businessId);

    boolean existsByBusinessIdAndName(UUID businessId, String name);

    boolean existsByBusinessIdAndNameAndIdNot(UUID businessId, String name, UUID queueId);
}
