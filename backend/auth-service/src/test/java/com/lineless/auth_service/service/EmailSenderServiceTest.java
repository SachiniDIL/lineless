package com.lineless.auth_service.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.test.util.ReflectionTestUtils;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class EmailSenderServiceTest {

    @Mock
    private JavaMailSender mailSender;

    @InjectMocks
    private EmailSenderService emailSenderService;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(
                emailSenderService,
                "frontendBaseUrl",
                "http://localhost:3000"
        );

        ReflectionTestUtils.setField(
                emailSenderService,
                "fromAddress",
                "noreply@lineless.local"
        );
    }

    @Test
    void shouldSendVerificationEmail() {

        emailSenderService.sendVerificationEmail(
                "test@example.com",
                "Test Customer",
                "sample-token+value"
        );

        ArgumentCaptor<SimpleMailMessage> messageCaptor =
                ArgumentCaptor.forClass(SimpleMailMessage.class);

        verify(mailSender).send(messageCaptor.capture());

        SimpleMailMessage message = messageCaptor.getValue();

        assertThat(message.getFrom())
                .isEqualTo("noreply@lineless.local");

        assertThat(message.getTo())
                .containsExactly("test@example.com");

        assertThat(message.getSubject())
                .isEqualTo("Verify your LineLess account");

        assertThat(message.getText())
                .contains("Hello Test Customer")
                .contains("Welcome to LineLess!")
                .contains("http://localhost:3000/verify-email?token=sample-token%2Bvalue")
                .contains("This link expires in 15 minutes.");
    }
}
