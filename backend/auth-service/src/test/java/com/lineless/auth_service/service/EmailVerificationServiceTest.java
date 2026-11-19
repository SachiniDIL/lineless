package com.lineless.auth_service.service;

import com.lineless.auth_service.entity.EmailVerificationToken;
import com.lineless.auth_service.entity.User;
import com.lineless.auth_service.exception.InvalidEmailVerificationTokenException;
import com.lineless.auth_service.repository.EmailVerificationTokenRepository;
import com.lineless.auth_service.repository.UserRepository;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class EmailVerificationServiceTest {

    @Mock
    private EmailVerificationTokenRepository tokenRepository;

    @Mock
    private UserRepository userRepository;

    private EmailVerificationService verificationService;

    @BeforeEach
    void setUp() {
        verificationService = new EmailVerificationService(
                tokenRepository,
                userRepository
        );
    }

    @Test
    void shouldVerifyEmailWithValidToken() {

        // Arrange
        String rawToken = "valid-random-token";
        String tokenHash =
                EmailVerificationTokenService.hashTokenValue(rawToken);

        User user = createUser(false);

        EmailVerificationToken token = createToken(
                user,
                tokenHash,
                OffsetDateTime.now(ZoneOffset.UTC).plusMinutes(10),
                null
        );

        when(tokenRepository.findByTokenHashForUpdate(tokenHash))
                .thenReturn(Optional.of(token));

        // Act
        verificationService.verifyEmail(rawToken);

        // Assert
        assertThat(user.isEmailVerified()).isTrue();
        assertThat(user.getEmailVerifiedAt()).isNotNull();

        assertThat(token.getUsedAt()).isNotNull();

        verify(userRepository).save(user);
        verify(tokenRepository).save(token);
    }

    @Test
    void shouldRejectExpiredToken() {

        // Arrange
        String rawToken = "expired-token";
        String tokenHash =
                EmailVerificationTokenService.hashTokenValue(rawToken);

        User user = createUser(false);

        EmailVerificationToken token = createToken(
                user,
                tokenHash,
                OffsetDateTime.now(ZoneOffset.UTC).minusMinutes(1),
                null
        );

        when(tokenRepository.findByTokenHashForUpdate(tokenHash))
                .thenReturn(Optional.of(token));

        // Act & Assert
        assertThatThrownBy(() ->
                verificationService.verifyEmail(rawToken)
        ).isInstanceOf(InvalidEmailVerificationTokenException.class);

        assertThat(user.isEmailVerified()).isFalse();
        assertThat(token.getUsedAt()).isNull();

        verify(userRepository, never()).save(any());
        verify(tokenRepository, never()).save(any());
    }

    @Test
    void shouldRejectAlreadyUsedToken() {

        // Arrange
        String rawToken = "already-used-token";
        String tokenHash =
                EmailVerificationTokenService.hashTokenValue(rawToken);

        User user = createUser(false);

        EmailVerificationToken token = createToken(
                user,
                tokenHash,
                OffsetDateTime.now(ZoneOffset.UTC).plusMinutes(10),
                OffsetDateTime.now(ZoneOffset.UTC).minusMinutes(1)
        );

        when(tokenRepository.findByTokenHashForUpdate(tokenHash))
                .thenReturn(Optional.of(token));

        // Act & Assert
        assertThatThrownBy(() ->
                verificationService.verifyEmail(rawToken)
        ).isInstanceOf(InvalidEmailVerificationTokenException.class);

        assertThat(user.isEmailVerified()).isFalse();

        verify(userRepository, never()).save(any());
        verify(tokenRepository, never()).save(any());
    }

    @Test
    void shouldRejectUnknownToken() {

        // Arrange
        String rawToken = "unknown-token";
        String tokenHash =
                EmailVerificationTokenService.hashTokenValue(rawToken);

        when(tokenRepository.findByTokenHashForUpdate(tokenHash))
                .thenReturn(Optional.empty());

        // Act & Assert
        assertThatThrownBy(() ->
                verificationService.verifyEmail(rawToken)
        ).isInstanceOf(InvalidEmailVerificationTokenException.class);

        verify(userRepository, never()).save(any());
        verify(tokenRepository, never()).save(any());
    }

    @Test
    void shouldRejectEmptyToken() {

        // Act & Assert
        assertThatThrownBy(() ->
                verificationService.verifyEmail(" ")
        ).isInstanceOf(InvalidEmailVerificationTokenException.class);

        verifyNoInteractions(tokenRepository);
        verifyNoInteractions(userRepository);
    }

    @Test
    void shouldNotChangeAlreadyVerifiedUser() {

        // Arrange
        String rawToken = "valid-token-for-verified-user";
        String tokenHash =
                EmailVerificationTokenService.hashTokenValue(rawToken);

        User user = createUser(true);

        EmailVerificationToken token = createToken(
                user,
                tokenHash,
                OffsetDateTime.now(ZoneOffset.UTC).plusMinutes(10),
                null
        );

        when(tokenRepository.findByTokenHashForUpdate(tokenHash))
                .thenReturn(Optional.of(token));

        // Act
        verificationService.verifyEmail(rawToken);

        // Assert
        assertThat(user.isEmailVerified()).isTrue();

        verify(userRepository, never()).save(any());

        // The token should still be consumed.
        assertThat(token.getUsedAt()).isNotNull();
        verify(tokenRepository).save(token);
    }

    private User createUser(boolean emailVerified) {

        User user = new User();

        user.setId(UUID.randomUUID());
        user.setName("Test User");
        user.setEmail("test@example.com");
        user.setEmailVerified(emailVerified);

        if (emailVerified) {
            user.setEmailVerifiedAt(
                    OffsetDateTime.now(ZoneOffset.UTC).minusDays(1)
            );
        }

        return user;
    }

    private EmailVerificationToken createToken(
            User user,
            String tokenHash,
            OffsetDateTime expiresAt,
            OffsetDateTime usedAt
    ) {

        EmailVerificationToken token = new EmailVerificationToken();

        token.setId(UUID.randomUUID());
        token.setUser(user);
        token.setTokenHash(tokenHash);
        token.setExpiresAt(expiresAt);
        token.setUsedAt(usedAt);

        return token;
    }
}
