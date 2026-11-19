package com.lineless.auth_service.service;

import com.lineless.auth_service.dto.request.RegisterRequest;
import com.lineless.auth_service.entity.User;
import com.lineless.auth_service.exception.RateLimitExceededException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Locale;

@Service
@RequiredArgsConstructor
public class RegistrationWorkflowService {

    private final RegistrationService registrationService;
    private final EmailVerificationTokenService emailVerificationTokenService;
    private final EmailSenderService emailSenderService;
    private final RegistrationRateLimitService registrationRateLimitService;

    public void register(RegisterRequest request, String clientIp) {

        String normalizedEmail =
                request.email().trim().toLowerCase(Locale.ROOT);

        // 1. Check whether registration attempts are allowed.
        boolean allowed = registrationRateLimitService.isAllowed(
                normalizedEmail,
                clientIp
        );

        if (!allowed) {
            throw new RateLimitExceededException();
        }

        // 2. Record the attempt before processing registration.
        registrationRateLimitService.recordAttempt(
                normalizedEmail,
                clientIp
        );

        // 3. Create and save the user.
        User user = registrationService.register(request);

        // 4. Generate and save the verification token.
        String rawToken = emailVerificationTokenService
                .createVerificationToken(user);

        // 5. Send the verification email.
        emailSenderService.sendVerificationEmail(
                user.getEmail(),
                user.getName(),
                rawToken
        );
    }
}
