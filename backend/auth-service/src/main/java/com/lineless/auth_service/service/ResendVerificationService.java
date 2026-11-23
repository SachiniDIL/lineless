package com.lineless.auth_service.service;

import com.lineless.auth_service.entity.User;
import com.lineless.auth_service.exception.RateLimitExceededException;
import com.lineless.auth_service.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.HexFormat;
import java.util.Locale;

@Service
public class ResendVerificationService {

    private final UserRepository userRepository;
    private final EmailVerificationTokenService emailVerificationTokenService;
    private final EmailSenderService emailSenderService;
    private final RateLimitService rateLimitService;

    public ResendVerificationService(UserRepository userRepository, EmailVerificationTokenService emailVerificationTokenService, EmailSenderService emailSenderService, RateLimitService rateLimitService) {
        this.userRepository = userRepository;
        this.emailVerificationTokenService = emailVerificationTokenService;
        this.emailSenderService = emailSenderService;
        this.rateLimitService = rateLimitService;
    }

    public void resend(String email, String clientIp) {

        String normalizedEmail = email.trim().toLowerCase(Locale.ROOT);

        boolean emailAllowed = rateLimitService.tryConsume(
                "lineless:auth:rate-limit:resend:email:" + hash(normalizedEmail),
                3,
                Duration.ofMinutes(15)
        );

        boolean ipAllowed = rateLimitService.tryConsume(
                "lineless:auth:rate-limit:resend:ip:" + hash(clientIp),
                10,
                Duration.ofHours(1)
        );

        if (!emailAllowed || !ipAllowed) {
            throw new RateLimitExceededException();
        }

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

    private String hash(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");

            byte[] hash = digest.digest(
                    value.getBytes(StandardCharsets.UTF_8)
            );

            return HexFormat.of().formatHex(hash);

        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 algorithm is unavailable", exception);
        }
    }
}