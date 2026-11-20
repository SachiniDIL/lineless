package com.lineless.auth_service.service;

import com.lineless.auth_service.dto.request.ForgotPasswordRequest;
import com.lineless.auth_service.entity.User;
import com.lineless.auth_service.exception.RateLimitExceededException;
import com.lineless.auth_service.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Locale;

@Service
@RequiredArgsConstructor
public class ForgotPasswordService {

    private final UserRepository userRepository;
    private final PasswordResetTokenService passwordResetTokenService;
    private final EmailSenderService emailSenderService;
    private final PasswordResetRateLimitService passwordResetRateLimitService;

    public void requestPasswordReset(
            ForgotPasswordRequest request,
            String clientIp
    ) {

        String normalizedEmail =
                request.email().trim().toLowerCase(Locale.ROOT);

        boolean allowed = passwordResetRateLimitService.isAllowed(
                normalizedEmail,
                clientIp
        );

        if (!allowed) {
            throw new RateLimitExceededException();
        }

        passwordResetRateLimitService.recordAttempt(
                normalizedEmail,
                clientIp
        );

        User user = userRepository.findByEmail(normalizedEmail)
                .orElse(null);

        if (user == null || user.getStatus() != com.lineless.auth_service.entity.UserStatus.ACTIVE) {
            return;
        }

        String rawToken =
                passwordResetTokenService.createPasswordResetToken(user);

        emailSenderService.sendPasswordResetEmail(
                user.getEmail(),
                user.getName(),
                rawToken
        );
    }
}