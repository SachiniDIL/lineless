package com.lineless.auth_service.service;

import com.lineless.auth_service.dto.request.RegisterRequest;
import com.lineless.auth_service.entity.User;
import com.lineless.auth_service.exception.RateLimitExceededException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.mail.MailException;
import org.springframework.stereotype.Service;

import java.util.Locale;
import java.util.Optional;

@Service
@Slf4j
@RequiredArgsConstructor
public class RegistrationWorkflowService {

    private final RegistrationService registrationService;
    private final EmailVerificationTokenService emailVerificationTokenService;
    private final EmailSenderService emailSenderService;
    private final RegistrationRateLimitService registrationRateLimitService;

    public void register(RegisterRequest request, String clientIp) {

        String normalizedEmail =
                request.email().trim().toLowerCase(Locale.ROOT);

        boolean allowed = registrationRateLimitService.isAllowed(
                normalizedEmail,
                clientIp
        );

        if (!allowed) {
            throw new RateLimitExceededException();
        }

        registrationRateLimitService.recordAttempt(
                normalizedEmail,
                clientIp
        );

        Optional<User> registrationResult =
                registrationService.register(request);

        // Email already exists. Do not reveal this to the caller.
        if (registrationResult.isEmpty()) {
            return;
        }

        User user = registrationResult.get();

        String rawToken =
                emailVerificationTokenService.createVerificationToken(user);

        try {
            emailSenderService.sendVerificationEmail(
                    user.getEmail(),
                    user.getName(),
                    rawToken
            );
        } catch (MailException exception) {

            log.error(
                    "Failed to send verification email for userId={}",
                    user.getId(),
                    exception
            );
        }
    }
}
