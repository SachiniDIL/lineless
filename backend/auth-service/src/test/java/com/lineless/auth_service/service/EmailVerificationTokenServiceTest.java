package com.lineless.auth_service.service;

import com.lineless.auth_service.entity.EmailVerificationToken;
import com.lineless.auth_service.entity.User;
import com.lineless.auth_service.repository.EmailVerificationTokenRepository;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class EmailVerificationTokenServiceTest {

    @Mock
    private EmailVerificationTokenRepository tokenRepository;

    private EmailVerificationTokenService tokenService;

    @BeforeEach
    void setUp() {
        tokenService = new EmailVerificationTokenService(tokenRepository);
    }

    @Test
    void shouldGenerateSecureTokenAndSaveItsHash() {

        // Arrange
        User user = new User();
        user.setId(UUID.randomUUID());

        OffsetDateTime beforeCreation =
                OffsetDateTime.now(ZoneOffset.UTC);

        // Act
        String rawToken = tokenService.createVerificationToken(user);

        OffsetDateTime afterCreation =
                OffsetDateTime.now(ZoneOffset.UTC);

        // Assert: Raw token should be generated.
        assertThat(rawToken).isNotBlank();
        assertThat(rawToken).hasSize(43);

        // Capture the token entity saved in the database.
        ArgumentCaptor<EmailVerificationToken> captor =
                ArgumentCaptor.forClass(EmailVerificationToken.class);

        verify(tokenRepository).save(captor.capture());

        EmailVerificationToken savedToken = captor.getValue();

        // Only the hash should be stored.
        assertThat(savedToken.getTokenHash())
                .isEqualTo(
                        EmailVerificationTokenService.hashTokenValue(rawToken)
                );

        assertThat(savedToken.getTokenHash())
                .isNotEqualTo(rawToken);

        assertThat(savedToken.getTokenHash()).hasSize(64);

        // The token must belong to the correct user.
        assertThat(savedToken.getUser()).isSameAs(user);

        // Expiration must be exactly 15 minutes after creation.
        assertThat(savedToken.getExpiresAt())
                .isAfterOrEqualTo(beforeCreation.plusMinutes(15));

        assertThat(savedToken.getExpiresAt())
                .isBeforeOrEqualTo(afterCreation.plusMinutes(15));

        assertThat(savedToken.getUsedAt()).isNull();

        // Previous unused tokens must be invalidated.
        verify(tokenRepository).invalidateUnusedTokensForUser(
                eq(user.getId()),
                any(OffsetDateTime.class)
        );
    }

    @Test
    void shouldGenerateDifferentTokensForDifferentRequests() {

        // Arrange
        User user = new User();
        user.setId(UUID.randomUUID());

        // Act
        String firstToken = tokenService.createVerificationToken(user);
        String secondToken = tokenService.createVerificationToken(user);

        // Assert
        assertThat(firstToken).isNotEqualTo(secondToken);

        verify(tokenRepository, times(2))
                .invalidateUnusedTokensForUser(
                        eq(user.getId()),
                        any(OffsetDateTime.class)
                );

        verify(tokenRepository, times(2))
                .save(any(EmailVerificationToken.class));
    }
}
