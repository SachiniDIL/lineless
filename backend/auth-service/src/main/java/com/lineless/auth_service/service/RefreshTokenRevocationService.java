package com.lineless.auth_service.service;

import java.time.OffsetDateTime;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.lineless.auth_service.repository.RefreshTokenRepository;

import lombok.RequiredArgsConstructor;

@Service
public class RefreshTokenRevocationService {

    private final RefreshTokenRepository refreshTokenRepository;

    public RefreshTokenRevocationService(RefreshTokenRepository refreshTokenRepository) {
        this.refreshTokenRepository = refreshTokenRepository;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void revokeTokenFamily(UUID familyId) {

        refreshTokenRepository.revokeActiveTokensByFamilyId(
                familyId,
                OffsetDateTime.now()
        );
    }
}