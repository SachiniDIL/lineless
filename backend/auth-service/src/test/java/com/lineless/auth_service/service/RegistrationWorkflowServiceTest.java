package com.lineless.auth_service.service;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.ArgumentMatchers.any;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.lineless.auth_service.dto.request.RegisterRequest;
import com.lineless.auth_service.entity.User;

@ExtendWith(MockitoExtension.class)
class RegistrationWorkflowServiceTest {

    @Mock
    private RegistrationService registrationService;

    @Mock
    private EmailVerificationTokenService emailVerificationTokenService;

    @Mock
    private EmailSenderService emailSenderService;

    private RegistrationWorkflowService registrationWorkflowService;

    @BeforeEach
    void setUp() {
        registrationWorkflowService = new RegistrationWorkflowService(
                registrationService,
                emailVerificationTokenService,
                emailSenderService);
    }

    @Test
    void registersUserGeneratesVerificationTokenAndSendsEmail() {
        RegisterRequest request = request();
        User user = user();
        String verificationToken = "verification-token";

        when(registrationService.register(request)).thenReturn(user);
        when(emailVerificationTokenService.createVerificationToken(user))
                .thenReturn(verificationToken);

        registrationWorkflowService.register(request);

        InOrder inOrder = inOrder(
                registrationService,
                emailVerificationTokenService,
                emailSenderService);
        inOrder.verify(registrationService).register(request);
        inOrder.verify(emailVerificationTokenService)
                .createVerificationToken(user);
        inOrder.verify(emailSenderService).sendVerificationEmail(
                "jane@example.com",
                "Jane Smith",
                verificationToken);
    }

    @Test
    void stopsWorkflowWhenRegistrationFails() {
        RegisterRequest request = request();
        RuntimeException exception = new RuntimeException("registration failed");
        when(registrationService.register(request)).thenThrow(exception);

        assertThatThrownBy(() -> registrationWorkflowService.register(request))
                .isSameAs(exception);

        verify(emailVerificationTokenService, never())
                .createVerificationToken(any(User.class));
        verify(emailSenderService, never())
                .sendVerificationEmail(any(), any(), any());
    }

    @Test
    void stopsWorkflowWhenVerificationTokenGenerationFails() {
        RegisterRequest request = request();
        User user = user();
        RuntimeException exception = new RuntimeException("token generation failed");
        when(registrationService.register(request)).thenReturn(user);
        when(emailVerificationTokenService.createVerificationToken(user))
                .thenThrow(exception);

        assertThatThrownBy(() -> registrationWorkflowService.register(request))
                .isSameAs(exception);

        verify(emailSenderService, never())
                .sendVerificationEmail(any(), any(), any());
    }

    @Test
    void propagatesEmailSendingFailure() {
        RegisterRequest request = request();
        User user = user();
        String verificationToken = "verification-token";
        RuntimeException exception = new RuntimeException("email sending failed");
        when(registrationService.register(request)).thenReturn(user);
        when(emailVerificationTokenService.createVerificationToken(user))
                .thenReturn(verificationToken);
        doThrow(exception).when(emailSenderService).sendVerificationEmail(
                user.getEmail(), user.getName(), verificationToken);

        assertThatThrownBy(() -> registrationWorkflowService.register(request))
                .isSameAs(exception);

        verify(emailSenderService).sendVerificationEmail(
                user.getEmail(), user.getName(), verificationToken);
    }

    private RegisterRequest request() {
        return new RegisterRequest("Jane Smith", "jane@example.com", "password123");
    }

    private User user() {
        User user = new User();
        user.setName("Jane Smith");
        user.setEmail("jane@example.com");
        return user;
    }
}
