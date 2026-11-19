package com.lineless.auth_service.integration;

import com.lineless.auth_service.service.EmailSenderService;

import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@ActiveProfiles("dev")
class EmailSenderIntegrationTest {

    @Autowired
    private EmailSenderService emailSenderService;

    @Test
    void shouldSendVerificationEmail() {

        emailSenderService.sendVerificationEmail(
                "test@example.com",
                "Test Customer",
                "sample-verification-token-123"
        );
    }
}