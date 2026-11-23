package com.lineless.auth_service.repository;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.lineless.auth_service.entity.IdentityProvider;
import com.lineless.auth_service.entity.UserIdentity;

public interface UserIdentityRepository
        extends JpaRepository<UserIdentity, UUID> {

    Optional<UserIdentity> findByProviderAndProviderSubject(
            IdentityProvider provider,
            String providerSubject
    );

    boolean existsByUser_IdAndProvider(
            UUID userId,
            IdentityProvider provider
    );
}