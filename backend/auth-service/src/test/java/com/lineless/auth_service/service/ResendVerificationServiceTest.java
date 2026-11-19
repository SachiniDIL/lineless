package com.lineless.auth_service.service;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.Mockito.*;

import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.lineless.auth_service.entity.User;
import com.lineless.auth_service.entity.UserStatus;
import com.lineless.auth_service.repository.UserRepository;

@ExtendWith(MockitoExtension.class)
class ResendVerificationServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private EmailVerificationTokenService emailVerificationTokenService;

    @Mock
    private EmailSenderService emailSenderService;

    private ResendVerificationService resendVerificationService;

    @BeforeEach
    void setUp() {
        resendVerificationService = new ResendVerificationService(
                userRepository,
                emailVerificationTokenService,
                emailSenderService
        );
    }

    @Test
    void shouldSendNewVerificationEmailToUnverifiedUser() {

        User user = createUser(false);

        when(userRepository.findByEmail("jane@example.com"))
                .thenReturn(Optional.of(user));

        when(emailVerificationTokenService.createVerificationToken(user))
                .thenReturn("new-verification-token");

        resendVerificationService.resend("jane@example.com");

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

        when(userRepository.findByEmail("missing@example.com"))
                .thenReturn(Optional.empty());

        assertThatCode(() ->
                resendVerificationService.resend("missing@example.com")
        ).doesNotThrowAnyException();

        verifyNoInteractions(
                emailVerificationTokenService,
                emailSenderService
        );
    }

    @Test
    void shouldNotSendEmailWhenUserIsAlreadyVerified() {

        User user = createUser(true);

        when(userRepository.findByEmail("jane@example.com"))
                .thenReturn(Optional.of(user));

        resendVerificationService.resend("jane@example.com");

        verifyNoInteractions(
                emailVerificationTokenService,
                emailSenderService
        );
    }

    @Test
    void shouldNormalizeEmailBeforeSearching() {

        User user = createUser(false);

        when(userRepository.findByEmail("jane@example.com"))
                .thenReturn(Optional.of(user));

        when(emailVerificationTokenService.createVerificationToken(user))
                .thenReturn("new-verification-token");

        resendVerificationService.resend("  JANE@EXAMPLE.COM  ");

        verify(userRepository).findByEmail("jane@example.com");

        verify(emailSenderService).sendVerificationEmail(
                "jane@example.com",
                "Jane Smith",
                "new-verification-token"
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