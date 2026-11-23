package com.lineless.auth_service.service;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.time.Duration;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.lineless.auth_service.entity.User;
import com.lineless.auth_service.entity.UserStatus;
import com.lineless.auth_service.exception.RateLimitExceededException;
import com.lineless.auth_service.repository.UserRepository;

@ExtendWith(MockitoExtension.class)
class ResendVerificationServiceTest {

    private static final String CLIENT_IP = "192.168.1.10";

    @Mock
    private UserRepository userRepository;

    @Mock
    private EmailVerificationTokenService emailVerificationTokenService;

    @Mock
    private EmailSenderService emailSenderService;

    @Mock
    private RateLimitService rateLimitService;

    private ResendVerificationService resendVerificationService;

    @BeforeEach
    void setUp() {
        resendVerificationService = new ResendVerificationService(
                userRepository,
                emailVerificationTokenService,
                emailSenderService,
                rateLimitService
        );
    }

    @Test
    void shouldSendNewVerificationEmailToUnverifiedUser() {

        User user = createUser(false);
        allowBothRateLimits();

        when(userRepository.findByEmail("jane@example.com"))
                .thenReturn(Optional.of(user));

        when(emailVerificationTokenService.createVerificationToken(user))
                .thenReturn("new-verification-token");

        resendVerificationService.resend("jane@example.com", CLIENT_IP);

        verifyRateLimits();

        verify(emailVerificationTokenService)
                .createVerificationToken(user);

        verify(emailSenderService).sendVerificationEmail(
                "jane@example.com",
                "Jane Smith",
                "new-verification-token"
        );
    }

    @Test
    void shouldNotSendEmailWhenUserDoesNotExist() {

        allowBothRateLimits();

        when(userRepository.findByEmail("missing@example.com"))
                .thenReturn(Optional.empty());

        assertThatCode(() ->
                resendVerificationService.resend(
                        "missing@example.com",
                        CLIENT_IP
                )
        ).doesNotThrowAnyException();

        verifyRateLimits();

        verify(userRepository).findByEmail("missing@example.com");

        verifyNoInteractions(
                emailVerificationTokenService,
                emailSenderService
        );
    }

    @Test
    void shouldNotSendEmailWhenUserIsAlreadyVerified() {

        User user = createUser(true);
        allowBothRateLimits();

        when(userRepository.findByEmail("jane@example.com"))
                .thenReturn(Optional.of(user));

        resendVerificationService.resend("jane@example.com", CLIENT_IP);

        verifyRateLimits();

        verifyNoInteractions(
                emailVerificationTokenService,
                emailSenderService
        );
    }

    @Test
    void shouldNormalizeEmailBeforeSearching() {

        User user = createUser(false);
        allowBothRateLimits();

        when(userRepository.findByEmail("jane@example.com"))
                .thenReturn(Optional.of(user));

        when(emailVerificationTokenService.createVerificationToken(user))
                .thenReturn("new-verification-token");

        resendVerificationService.resend(
                "  JANE@EXAMPLE.COM  ",
                CLIENT_IP
        );

        verify(userRepository).findByEmail("jane@example.com");

        verify(rateLimitService).tryConsume(
                argThat(key -> key.startsWith(
                        "lineless:auth:rate-limit:resend:email:"
                )),
                eq(3L),
                eq(Duration.ofMinutes(15))
        );

        verify(emailSenderService).sendVerificationEmail(
                "jane@example.com",
                "Jane Smith",
                "new-verification-token"
        );
    }

    @Test
    void shouldRejectRequestWhenEmailRateLimitIsExceeded() {

        when(rateLimitService.tryConsume(
                startsWith("lineless:auth:rate-limit:resend:email:"),
                eq(3L),
                eq(Duration.ofMinutes(15))
        )).thenReturn(false);

        when(rateLimitService.tryConsume(
                startsWith("lineless:auth:rate-limit:resend:ip:"),
                eq(10L),
                eq(Duration.ofHours(1))
        )).thenReturn(true);

        assertThatThrownBy(() ->
                resendVerificationService.resend(
                        "jane@example.com",
                        CLIENT_IP
                )
        ).isInstanceOf(RateLimitExceededException.class);

        verifyNoInteractions(
                userRepository,
                emailVerificationTokenService,
                emailSenderService
        );
    }

    @Test
    void shouldRejectRequestWhenIpRateLimitIsExceeded() {

        when(rateLimitService.tryConsume(
                startsWith("lineless:auth:rate-limit:resend:email:"),
                eq(3L),
                eq(Duration.ofMinutes(15))
        )).thenReturn(true);

        when(rateLimitService.tryConsume(
                startsWith("lineless:auth:rate-limit:resend:ip:"),
                eq(10L),
                eq(Duration.ofHours(1))
        )).thenReturn(false);

        assertThatThrownBy(() ->
                resendVerificationService.resend(
                        "jane@example.com",
                        CLIENT_IP
                )
        ).isInstanceOf(RateLimitExceededException.class);

        verifyNoInteractions(
                userRepository,
                emailVerificationTokenService,
                emailSenderService
        );
    }

    private void allowBothRateLimits() {

        when(rateLimitService.tryConsume(
                anyString(),
                anyLong(),
                any(Duration.class)
        )).thenReturn(true);
    }

    private void verifyRateLimits() {

        verify(rateLimitService).tryConsume(
                startsWith("lineless:auth:rate-limit:resend:email:"),
                eq(3L),
                eq(Duration.ofMinutes(15))
        );

        verify(rateLimitService).tryConsume(
                startsWith("lineless:auth:rate-limit:resend:ip:"),
                eq(10L),
                eq(Duration.ofHours(1))
        );
    }

    private User createUser(boolean emailVerified) {

        User user = new User();

        user.setId(UUID.randomUUID());
        user.setName("Jane Smith");
        user.setEmail("jane@example.com");
        user.setPasswordHash("hashed-password");
        user.setStatus(UserStatus.ACTIVE);
        user.setEmailVerified(emailVerified);

        return user;
    }
}
