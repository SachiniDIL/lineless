package com.lineless.auth_service.service;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class EmailSenderService {

    private final JavaMailSender mailSender;

    @Value("${app.frontend.base-url}")
    private String frontendBaseUrl;

    @Value("${app.mail.from}")
    private String fromAddress;

    public void sendVerificationEmail(
            String recipientEmail,
            String recipientName,
            String rawToken
    ) {
        String verificationLink = frontendBaseUrl
                + "/verify-email?token="
                + URLEncoder.encode(rawToken, StandardCharsets.UTF_8);

        SimpleMailMessage message = new SimpleMailMessage();

        message.setFrom(fromAddress);
        message.setTo(recipientEmail);
        message.setSubject("Verify your LineLess account");

        message.setText("""
                Hello %s,

                Welcome to LineLess!

                Please verify your email address by clicking the link below:

                %s

                This link expires in 15 minutes.

                If you did not create a LineLess account, you can ignore this email.

                Thank you,
                LineLess Team
                """.formatted(recipientName, verificationLink));

        mailSender.send(message);
    }
}