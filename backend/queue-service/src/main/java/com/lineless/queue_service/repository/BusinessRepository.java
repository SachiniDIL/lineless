package com.lineless.queue_service.repository;

import com.lineless.queue_service.entity.Business;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface BusinessRepository extends JpaRepository<Business, UUID> {

    List<Business> findByOwnerUserId(UUID ownerUserId);

    List<Business> findByIsPublicTrue();
}
