
package com.lineless.auth_service.service;

import com.lineless.auth_service.dto.request.ForgotPasswordRequest;
import com.lineless.auth_service.entity.User;
import com.lineless.auth_service.entity.UserStatus;
import com.lineless.auth_service.exception.RateLimitExceededException;
import com.lineless.auth_service.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.mail.MailException;
import org.springframework.stereotype.Service;

import java.util.Locale;

@Service
@Slf4j
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

        if (user == null || user.getStatus() != UserStatus.ACTIVE) {
            return;
        }

        String rawToken =
                passwordResetTokenService.createPasswordResetToken(user);

        try {
            emailSenderService.sendPasswordResetEmail(
                    user.getEmail(),
                    user.getName(),
                    rawToken
            );
        } catch (MailException exception) {

            log.error(
                    "Failed to send password reset email for userId={}",
                    user.getId(),
                    exception
            );
        }
    }
}
