package com.lineless.auth_service.service;

import com.lineless.auth_service.entity.PasswordResetToken;
import com.lineless.auth_service.entity.User;
import com.lineless.auth_service.exception.InvalidPasswordResetTokenException;
import com.lineless.auth_service.repository.PasswordResetTokenRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.OffsetDateTime;
import java.util.HexFormat;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PasswordResetTokenServiceTest {

    @Mock
    private PasswordResetTokenRepository passwordResetTokenRepository;

    @InjectMocks
    private PasswordResetTokenService passwordResetTokenService;

    private User user;

    @BeforeEach
    void setUp() {
        user = new User();
        user.setId(UUID.randomUUID());
    }

    // 1. Successfully creates a password reset token
    @Test
    void shouldCreatePasswordResetTokenSuccessfully() {

        OffsetDateTime beforeCreation = OffsetDateTime.now();

        String rawToken =
                passwordResetTokenService.createPasswordResetToken(user);

        OffsetDateTime afterCreation = OffsetDateTime.now();

        ArgumentCaptor<PasswordResetToken> tokenCaptor =
                ArgumentCaptor.forClass(PasswordResetToken.class);

        verify(passwordResetTokenRepository).save(tokenCaptor.capture());

        PasswordResetToken savedToken = tokenCaptor.getValue();

        assertNotNull(rawToken);
        assertFalse(rawToken.isBlank());

        // 32 random bytes encoded as Base64 URL-safe without padding
        assertEquals(43, rawToken.length());

        assertNotNull(savedToken.getTokenHash());
        assertNotEquals(rawToken, savedToken.getTokenHash());

        // Verify SHA-256 hash was stored instead of the raw token
        assertEquals(hashToken(rawToken), savedToken.getTokenHash());

        // Verify the token expires after 15 minutes
        assertFalse(
                savedToken.getExpiresAt()
                        .isBefore(beforeCreation.plusMinutes(15))
        );

        assertFalse(
                savedToken.getExpiresAt()
                        .isAfter(afterCreation.plusMinutes(15))
        );

        assertEquals(user, savedToken.getUser());

        verify(passwordResetTokenRepository)
                .invalidateUnusedTokensByUserId(
                        eq(user.getId()),
                        any(OffsetDateTime.class)
                );
    }

    // 2. Valid token should be returned
    @Test
    void shouldValidateValidPasswordResetToken() {

        String rawToken = "valid-reset-token";

        PasswordResetToken resetToken = createValidToken(rawToken);

        when(passwordResetTokenRepository.findByTokenHashForUpdate(
                hashToken(rawToken)
        )).thenReturn(Optional.of(resetToken));

        PasswordResetToken result =
                passwordResetTokenService.validatePasswordResetToken(rawToken);

        assertNotNull(result);
        assertEquals(resetToken, result);

        verify(passwordResetTokenRepository)
                .findByTokenHashForUpdate(hashToken(rawToken));
    }

    // 3. Null token should be rejected
    @Test
    void shouldRejectNullToken() {

        assertThrows(
                InvalidPasswordResetTokenException.class,
                () -> passwordResetTokenService.validatePasswordResetToken(null)
        );

        verifyNoInteractions(passwordResetTokenRepository);
    }

    // 4. Blank token should be rejected
    @Test
    void shouldRejectBlankToken() {

        assertThrows(
                InvalidPasswordResetTokenException.class,
                () -> passwordResetTokenService.validatePasswordResetToken(" ")
        );

        verifyNoInteractions(passwordResetTokenRepository);
    }

    // 5. Unknown token should be rejected
    @Test
    void shouldRejectUnknownToken() {

        String rawToken = "unknown-reset-token";

        when(passwordResetTokenRepository.findByTokenHashForUpdate(
                hashToken(rawToken)
        )).thenReturn(Optional.empty());

        assertThrows(
                InvalidPasswordResetTokenException.class,
                () -> passwordResetTokenService.validatePasswordResetToken(rawToken)
        );
    }

    // 6. Expired token should be rejected
    @Test
    void shouldRejectExpiredToken() {

        String rawToken = "expired-reset-token";

        PasswordResetToken resetToken = createValidToken(rawToken);
        resetToken.setExpiresAt(OffsetDateTime.now().minusMinutes(1));

        when(passwordResetTokenRepository.findByTokenHashForUpdate(
                hashToken(rawToken)
        )).thenReturn(Optional.of(resetToken));

        assertThrows(
                InvalidPasswordResetTokenException.class,
                () -> passwordResetTokenService.validatePasswordResetToken(rawToken)
        );
    }

    // 7. Already-used token should be rejected
    @Test
    void shouldRejectAlreadyUsedToken() {

        String rawToken = "already-used-reset-token";

        PasswordResetToken resetToken = createValidToken(rawToken);
        resetToken.setUsedAt(OffsetDateTime.now().minusMinutes(1));

        when(passwordResetTokenRepository.findByTokenHashForUpdate(
                hashToken(rawToken)
        )).thenReturn(Optional.of(resetToken));

        assertThrows(
                InvalidPasswordResetTokenException.class,
                () -> passwordResetTokenService.validatePasswordResetToken(rawToken)
        );
    }

    // 8. Token expiring exactly now should be rejected
    @Test
    void shouldRejectTokenThatHasReachedItsExpiryTime() {

        String rawToken = "just-expired-reset-token";

        PasswordResetToken resetToken = createValidToken(rawToken);
        resetToken.setExpiresAt(OffsetDateTime.now().minusSeconds(1));

        when(passwordResetTokenRepository.findByTokenHashForUpdate(
                hashToken(rawToken)
        )).thenReturn(Optional.of(resetToken));

        assertThrows(
                InvalidPasswordResetTokenException.class,
                () -> passwordResetTokenService.validatePasswordResetToken(rawToken)
        );
    }

    private PasswordResetToken createValidToken(String rawToken) {

        PasswordResetToken resetToken = new PasswordResetToken();

        resetToken.setUser(user);
        resetToken.setTokenHash(hashToken(rawToken));
        resetToken.setExpiresAt(OffsetDateTime.now().plusMinutes(15));

        return resetToken;
    }

    private String hashToken(String token) {

        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");

            byte[] hashBytes = digest.digest(
                    token.getBytes(StandardCharsets.UTF_8)
            );

            return HexFormat.of().formatHex(hashBytes);

        } catch (Exception exception) {
            throw new IllegalStateException(exception);
        }
    }
}
