package com.lineless.auth_service.service;

import java.util.Locale;

import org.springframework.stereotype.Service;

import com.lineless.auth_service.repository.UserRepository;

@Service
public class ResendVerificationService {

    private final UserRepository userRepository;
    private final EmailVerificationTokenService emailVerificationTokenService;
    private final EmailSenderService emailSenderService;

    public ResendVerificationService(UserRepository userRepository, EmailVerificationTokenService emailVerificationTokenService, EmailSenderService emailSenderService) {
        this.userRepository = userRepository;
        this.emailVerificationTokenService = emailVerificationTokenService;
        this.emailSenderService = emailSenderService;
    }

    public void resend(String email) {

        String normalizedEmail = email.trim().toLowerCase(Locale.ROOT);

        userRepository.findByEmail(normalizedEmail)
                .filter(user -> !user.isEmailVerified())
                .ifPresent(user -> {

                    String rawToken =
                            emailVerificationTokenService.createVerificationToken(user);

                    emailSenderService.sendVerificationEmail(
                            user.getEmail(),
                            user.getName(),
                            rawToken
                    );
                });
    }
}