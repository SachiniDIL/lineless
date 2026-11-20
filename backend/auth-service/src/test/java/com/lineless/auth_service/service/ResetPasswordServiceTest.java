package com.lineless.auth_service.service;

import com.lineless.auth_service.dto.request.ResetPasswordRequest;
import com.lineless.auth_service.entity.PasswordResetToken;
import com.lineless.auth_service.entity.User;
import com.lineless.auth_service.entity.UserStatus;
import com.lineless.auth_service.exception.InvalidPasswordResetTokenException;
import com.lineless.auth_service.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.OffsetDateTime;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ResetPasswordServiceTest {

    private final String rawToken = "valid-reset-token";
    private final String newPassword = "NewPassword123";
    @Mock
    private PasswordResetTokenService passwordResetTokenService;
    @Mock
    private UserRepository userRepository;
    @Mock
    private PasswordEncoder passwordEncoder;
    @Mock
    private RefreshTokenService refreshTokenService;
    @InjectMocks
    private ResetPasswordService resetPasswordService;
    private User activeUser;
    private PasswordResetToken resetToken;

    @BeforeEach
    void setUp() {

        activeUser = new User();
        activeUser.setId(UUID.randomUUID());
        activeUser.setName("Test User");
        activeUser.setEmail("test@example.com");
        activeUser.setStatus(UserStatus.ACTIVE);
        activeUser.setPasswordHash("old-password-hash");

        resetToken = new PasswordResetToken();
        resetToken.setUser(activeUser);
        resetToken.setTokenHash("hashed-reset-token");
        resetToken.setExpiresAt(OffsetDateTime.now().plusMinutes(15));
    }

    // 1. Successfully reset password
    @Test
    void shouldResetPasswordSuccessfully() {

        ResetPasswordRequest request =
                new ResetPasswordRequest(rawToken, newPassword);

        when(passwordResetTokenService.validatePasswordResetToken(rawToken))
                .thenReturn(resetToken);

        when(passwordEncoder.encode(newPassword))
                .thenReturn("encoded-new-password");

        resetPasswordService.resetPassword(request);

        assertEquals("encoded-new-password", activeUser.getPasswordHash());

        verify(userRepository).save(activeUser);

        assertNotNull(resetToken.getUsedAt());

        verify(refreshTokenService)
                .revokeAllUserSessions(activeUser.getId());
    }

    // 2. Password should be encoded before saving
    @Test
    void shouldEncodeNewPasswordBeforeSaving() {

        ResetPasswordRequest request =
                new ResetPasswordRequest(rawToken, newPassword);

        when(passwordResetTokenService.validatePasswordResetToken(rawToken))
                .thenReturn(resetToken);

        when(passwordEncoder.encode(newPassword))
                .thenReturn("bcrypt-encoded-value");

        resetPasswordService.resetPassword(request);

        verify(passwordEncoder).encode(newPassword);

        assertNotEquals(newPassword, activeUser.getPasswordHash());
        assertEquals("bcrypt-encoded-value", activeUser.getPasswordHash());
    }

    // 3. Reset token should be marked as used
    @Test
    void shouldMarkResetTokenAsUsed() {

        ResetPasswordRequest request =
                new ResetPasswordRequest(rawToken, newPassword);

        when(passwordResetTokenService.validatePasswordResetToken(rawToken))
                .thenReturn(resetToken);

        when(passwordEncoder.encode(newPassword))
                .thenReturn("encoded-password");

        resetPasswordService.resetPassword(request);

        assertNotNull(resetToken.getUsedAt());
    }

    // 4. All existing refresh sessions should be revoked
    @Test
    void shouldRevokeAllUserSessionsAfterPasswordReset() {

        ResetPasswordRequest request =
                new ResetPasswordRequest(rawToken, newPassword);

        when(passwordResetTokenService.validatePasswordResetToken(rawToken))
                .thenReturn(resetToken);

        when(passwordEncoder.encode(newPassword))
                .thenReturn("encoded-password");

        resetPasswordService.resetPassword(request);

        verify(refreshTokenService, times(1))
                .revokeAllUserSessions(activeUser.getId());
    }

    // 5. Invalid token should not update the password
    @Test
    void shouldNotResetPasswordWhenTokenIsInvalid() {

        ResetPasswordRequest request =
                new ResetPasswordRequest("invalid-token", newPassword);

        when(passwordResetTokenService.validatePasswordResetToken("invalid-token"))
                .thenThrow(new InvalidPasswordResetTokenException(
                        "Invalid or expired password reset link."
                ));

        assertThrows(
                InvalidPasswordResetTokenException.class,
                () -> resetPasswordService.resetPassword(request)
        );

        assertEquals("old-password-hash", activeUser.getPasswordHash());

        verify(userRepository, never()).save(any());

        verifyNoInteractions(passwordEncoder);
        verifyNoInteractions(refreshTokenService);
    }

    // 6. Expired token should not update the password
    @Test
    void shouldNotResetPasswordWhenTokenIsExpired() {

        ResetPasswordRequest request =
                new ResetPasswordRequest(rawToken, newPassword);

        when(passwordResetTokenService.validatePasswordResetToken(rawToken))
                .thenThrow(new InvalidPasswordResetTokenException(
                        "Invalid or expired password reset link."
                ));

        assertThrows(
                InvalidPasswordResetTokenException.class,
                () -> resetPasswordService.resetPassword(request)
        );

        assertEquals("old-password-hash", activeUser.getPasswordHash());

        verify(userRepository, never()).save(any());
        verifyNoInteractions(passwordEncoder);
        verifyNoInteractions(refreshTokenService);
    }

    // 7. Already-used token should not update the password
    @Test
    void shouldNotResetPasswordWhenTokenWasAlreadyUsed() {

        ResetPasswordRequest request =
                new ResetPasswordRequest(rawToken, newPassword);

        when(passwordResetTokenService.validatePasswordResetToken(rawToken))
                .thenThrow(new InvalidPasswordResetTokenException(
                        "Invalid or expired password reset link."
                ));

        assertThrows(
                InvalidPasswordResetTokenException.class,
                () -> resetPasswordService.resetPassword(request)
        );

        assertEquals("old-password-hash", activeUser.getPasswordHash());

        verify(userRepository, never()).save(any());
        verifyNoInteractions(passwordEncoder);
        verifyNoInteractions(refreshTokenService);
    }

    // 8. Inactive user should not be allowed to reset password
    @Test
    void shouldNotResetPasswordWhenUserIsInactive() {

        activeUser.setStatus(UserStatus.INACTIVE);

        ResetPasswordRequest request =
                new ResetPasswordRequest(rawToken, newPassword);

        when(passwordResetTokenService.validatePasswordResetToken(rawToken))
                .thenReturn(resetToken);

        assertThrows(
                InvalidPasswordResetTokenException.class,
                () -> resetPasswordService.resetPassword(request)
        );

        assertEquals("old-password-hash", activeUser.getPasswordHash());

        verify(userRepository, never()).save(any());
        verifyNoInteractions(passwordEncoder);
        verifyNoInteractions(refreshTokenService);
    }
}
