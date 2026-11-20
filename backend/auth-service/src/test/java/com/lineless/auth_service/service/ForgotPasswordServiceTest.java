package com.lineless.auth_service.service;

import com.lineless.auth_service.dto.request.ForgotPasswordRequest;
import com.lineless.auth_service.entity.User;
import com.lineless.auth_service.entity.UserStatus;
import com.lineless.auth_service.exception.RateLimitExceededException;
import com.lineless.auth_service.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ForgotPasswordServiceTest {

    private final String clientIp = "192.168.1.10";
    @Mock
    private UserRepository userRepository;
    @Mock
    private PasswordResetTokenService passwordResetTokenService;
    @Mock
    private EmailSenderService emailSenderService;
    @Mock
    private PasswordResetRateLimitService passwordResetRateLimitService;
    @InjectMocks
    private ForgotPasswordService forgotPasswordService;
    private User activeUser;

    @BeforeEach
    void setUp() {
        activeUser = new User();
        activeUser.setId(UUID.randomUUID());
        activeUser.setName("Test User");
        activeUser.setEmail("test@example.com");
        activeUser.setStatus(UserStatus.ACTIVE);
    }

    // 1. Existing active user should receive a password reset email
    @Test
    void shouldSendPasswordResetEmailForExistingActiveUser() {

        ForgotPasswordRequest request =
                new ForgotPasswordRequest("test@example.com");

        when(passwordResetRateLimitService.isAllowed(
                "test@example.com", clientIp
        )).thenReturn(true);

        when(userRepository.findByEmail("test@example.com"))
                .thenReturn(Optional.of(activeUser));

        when(passwordResetTokenService.createPasswordResetToken(activeUser))
                .thenReturn("generated-reset-token");

        forgotPasswordService.requestPasswordReset(request, clientIp);

        verify(passwordResetRateLimitService)
                .recordAttempt("test@example.com", clientIp);

        verify(passwordResetTokenService)
                .createPasswordResetToken(activeUser);

        verify(emailSenderService).sendPasswordResetEmail(
                "test@example.com",
                "Test User",
                "generated-reset-token"
        );
    }

    // 2. Unknown email should not send an email
    @Test
    void shouldNotSendEmailWhenUserDoesNotExist() {

        ForgotPasswordRequest request =
                new ForgotPasswordRequest("unknown@example.com");

        when(passwordResetRateLimitService.isAllowed(
                "unknown@example.com", clientIp
        )).thenReturn(true);

        when(userRepository.findByEmail("unknown@example.com"))
                .thenReturn(Optional.empty());

        assertDoesNotThrow(() ->
                forgotPasswordService.requestPasswordReset(request, clientIp)
        );

        verify(emailSenderService, never())
                .sendPasswordResetEmail(anyString(), anyString(), anyString());

        verify(passwordResetTokenService, never())
                .createPasswordResetToken(any());
    }

    // 3. Inactive user should not receive an email
    @Test
    void shouldNotSendEmailWhenUserIsInactive() {

        activeUser.setStatus(UserStatus.INACTIVE);

        ForgotPasswordRequest request =
                new ForgotPasswordRequest("test@example.com");

        when(passwordResetRateLimitService.isAllowed(
                "test@example.com", clientIp
        )).thenReturn(true);

        when(userRepository.findByEmail("test@example.com"))
                .thenReturn(Optional.of(activeUser));

        assertDoesNotThrow(() ->
                forgotPasswordService.requestPasswordReset(request, clientIp)
        );

        verify(emailSenderService, never())
                .sendPasswordResetEmail(anyString(), anyString(), anyString());

        verify(passwordResetTokenService, never())
                .createPasswordResetToken(any());
    }

    // 4. Email should be normalized before looking up the user
    @Test
    void shouldNormalizeEmailBeforeFindingUser() {

        ForgotPasswordRequest request =
                new ForgotPasswordRequest("  TEST@EXAMPLE.COM  ");

        when(passwordResetRateLimitService.isAllowed(
                "test@example.com", clientIp
        )).thenReturn(true);

        when(userRepository.findByEmail("test@example.com"))
                .thenReturn(Optional.of(activeUser));

        when(passwordResetTokenService.createPasswordResetToken(activeUser))
                .thenReturn("generated-reset-token");

        forgotPasswordService.requestPasswordReset(request, clientIp);

        verify(userRepository).findByEmail("test@example.com");

        verify(emailSenderService).sendPasswordResetEmail(
                "test@example.com",
                "Test User",
                "generated-reset-token"
        );
    }

    // 5. Email rate limit exceeded
    @Test
    void shouldRejectRequestWhenRateLimitIsExceeded() {

        ForgotPasswordRequest request =
                new ForgotPasswordRequest("test@example.com");

        when(passwordResetRateLimitService.isAllowed(
                "test@example.com", clientIp
        )).thenReturn(false);

        assertThrows(
                RateLimitExceededException.class,
                () -> forgotPasswordService.requestPasswordReset(request, clientIp)
        );

        verify(passwordResetRateLimitService, never())
                .recordAttempt(anyString(), anyString());

        verifyNoInteractions(userRepository);
        verifyNoInteractions(passwordResetTokenService);
        verifyNoInteractions(emailSenderService);
    }

    // 6. IP rate limit exceeded
    @Test
    void shouldRejectRequestWhenIpRateLimitIsExceeded() {

        ForgotPasswordRequest request =
                new ForgotPasswordRequest("test@example.com");

        when(passwordResetRateLimitService.isAllowed(
                "test@example.com", clientIp
        )).thenReturn(false);

        assertThrows(
                RateLimitExceededException.class,
                () -> forgotPasswordService.requestPasswordReset(request, clientIp)
        );

        verifyNoInteractions(userRepository);
        verifyNoInteractions(emailSenderService);
    }


    @Test
    void shouldNotExposeEmailFailureWhenSendingPasswordResetEmailFails() {

        ForgotPasswordRequest request =
                new ForgotPasswordRequest("test@example.com");

        User user = activeUser;

        when(passwordResetRateLimitService.isAllowed(
                "test@example.com", "127.0.0.1"
        )).thenReturn(true);

        when(userRepository.findByEmail("test@example.com"))
                .thenReturn(Optional.of(user));

        when(passwordResetTokenService.createPasswordResetToken(user))
                .thenReturn("raw-reset-token");

        doThrow(new org.springframework.mail.MailSendException(
                "Mail server unavailable"
        )).when(emailSenderService).sendPasswordResetEmail(
                anyString(),
                anyString(),
                anyString()
        );

        assertDoesNotThrow(() ->
                forgotPasswordService.requestPasswordReset(
                        request,
                        "127.0.0.1"
                )
        );

        verify(emailSenderService).sendPasswordResetEmail(
                anyString(),
                anyString(),
                anyString()
        );
    }
}
