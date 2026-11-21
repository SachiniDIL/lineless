package com.lineless.auth_service.service;

import com.lineless.auth_service.dto.request.RegisterRequest;
import com.lineless.auth_service.entity.User;
import com.lineless.auth_service.exception.RateLimitExceededException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class RegistrationWorkflowServiceTest {

    private static final String CLIENT_IP = "192.0.2.10";

    @Mock
    private RegistrationService registrationService;

    @Mock
    private EmailVerificationTokenService emailVerificationTokenService;

    @Mock
    private EmailSenderService emailSenderService;

    @Mock
    private RegistrationRateLimitService registrationRateLimitService;

    private RegistrationWorkflowService registrationWorkflowService;

    @BeforeEach
    void setUp() {
        registrationWorkflowService = new RegistrationWorkflowService(
                registrationService,
                emailVerificationTokenService,
                emailSenderService,
                registrationRateLimitService
        );
    }

    @Test
    void registersUserGeneratesVerificationTokenAndSendsEmail() {

        RegisterRequest request = request();
        User user = user();
        String verificationToken = "verification-token";

        when(registrationRateLimitService.tryConsume(
                "jane@example.com", CLIENT_IP))
                .thenReturn(true);

        when(registrationService.register(request))
                .thenReturn(Optional.of(user));

        when(emailVerificationTokenService.createVerificationToken(user))
                .thenReturn(verificationToken);

        registrationWorkflowService.register(request, CLIENT_IP);

        InOrder inOrder = inOrder(
                registrationRateLimitService,
                registrationService,
                emailVerificationTokenService,
                emailSenderService
        );

        inOrder.verify(registrationRateLimitService)
                .tryConsume("jane@example.com", CLIENT_IP);

        inOrder.verify(registrationService).register(request);

        inOrder.verify(emailVerificationTokenService)
                .createVerificationToken(user);

        inOrder.verify(emailSenderService).sendVerificationEmail(
                "jane@example.com",
                "Jane Smith",
                verificationToken
        );
    }

    @Test
    void rejectsRegistrationWhenRateLimitIsExceeded() {

        RegisterRequest request = request();

        when(registrationRateLimitService.tryConsume(
                "jane@example.com", CLIENT_IP))
                .thenReturn(false);

        assertThatThrownBy(() ->
                registrationWorkflowService.register(request, CLIENT_IP)
        ).isInstanceOf(RateLimitExceededException.class);

        verify(registrationRateLimitService)
                .tryConsume("jane@example.com", CLIENT_IP);

        verifyNoInteractions(
                registrationService,
                emailVerificationTokenService,
                emailSenderService
        );
    }

    @Test
    void stopsWorkflowWhenRegistrationFails() {

        RegisterRequest request = request();

        RuntimeException exception =
                new RuntimeException("registration failed");

        when(registrationRateLimitService.tryConsume(
                "jane@example.com", CLIENT_IP))
                .thenReturn(true);

        when(registrationService.register(request))
                .thenThrow(exception);

        assertThatThrownBy(() ->
                registrationWorkflowService.register(request, CLIENT_IP)
        ).isSameAs(exception);

        verify(registrationRateLimitService)
                .tryConsume("jane@example.com", CLIENT_IP);

        verify(emailVerificationTokenService, never())
                .createVerificationToken(any(User.class));

        verify(emailSenderService, never())
                .sendVerificationEmail(any(), any(), any());
    }

    @Test
    void stopsWorkflowWhenVerificationTokenGenerationFails() {

        RegisterRequest request = request();
        User user = user();

        RuntimeException exception =
                new RuntimeException("token generation failed");

        when(registrationRateLimitService.tryConsume(
                "jane@example.com", CLIENT_IP))
                .thenReturn(true);

        when(registrationService.register(request))
                .thenReturn(Optional.of(user));

        when(emailVerificationTokenService.createVerificationToken(user))
                .thenThrow(exception);

        assertThatThrownBy(() ->
                registrationWorkflowService.register(request, CLIENT_IP)
        ).isSameAs(exception);

        verify(emailSenderService, never())
                .sendVerificationEmail(any(), any(), any());
    }

    @Test
    void handlesEmailSendingFailureWithoutFailingRegistration() {

        RegisterRequest request = request();
        User user = user();

        String verificationToken = "verification-token";

        when(registrationRateLimitService.tryConsume(
                "jane@example.com", CLIENT_IP))
                .thenReturn(true);

        when(registrationService.register(request))
                .thenReturn(Optional.of(user));

        when(emailVerificationTokenService.createVerificationToken(user))
                .thenReturn(verificationToken);

        doThrow(new org.springframework.mail.MailSendException(
                "Mail server unavailable"
        )).when(emailSenderService).sendVerificationEmail(
                user.getEmail(),
                user.getName(),
                verificationToken
        );

        assertThatCode(() ->
                registrationWorkflowService.register(request, CLIENT_IP)
        ).doesNotThrowAnyException();

        verify(emailSenderService).sendVerificationEmail(
                user.getEmail(),
                user.getName(),
                verificationToken
        );
    }

    @Test
    void doesNotCreateVerificationTokenWhenEmailAlreadyExists() {

        RegisterRequest request = request();

        when(registrationRateLimitService.tryConsume(
                "jane@example.com", CLIENT_IP))
                .thenReturn(true);

        when(registrationService.register(request))
                .thenReturn(Optional.empty());

        registrationWorkflowService.register(request, CLIENT_IP);

        verify(registrationRateLimitService)
                .tryConsume("jane@example.com", CLIENT_IP);

        verify(registrationService).register(request);

        verifyNoInteractions(
                emailVerificationTokenService,
                emailSenderService
        );
    }

    private RegisterRequest request() {
        return new RegisterRequest(
                "Jane Smith",
                "jane@example.com",
                "password123"
        );
    }

    private User user() {
        User user = new User();
        user.setName("Jane Smith");
        user.setEmail("jane@example.com");
        return user;
    }
}
