package com.lineless.auth_service.service;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

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

    public void sendPasswordResetEmail(
            String email,
            String name,
            String rawToken
    ) {
        String resetLink = frontendBaseUrl
                + "/reset-password?token="
                + rawToken;

        SimpleMailMessage message = new SimpleMailMessage();

        message.setFrom(fromAddress);
        message.setTo(email);
        message.setSubject("Reset your LineLess password");

        message.setText("""
                Hello %s,
                
                We received a request to reset your LineLess password.
                
                Click the link below to choose a new password:
                
                %s
                
                This link expires in 15 minutes and can only be used once.
                
                If you did not request this, you can safely ignore this email.
                
                Regards,
                LineLess Team
                """.formatted(name, resetLink));

        mailSender.send(message);
    }
}