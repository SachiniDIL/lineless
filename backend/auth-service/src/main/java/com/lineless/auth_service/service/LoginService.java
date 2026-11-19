package com.lineless.auth_service.service;

import com.lineless.auth_service.dto.request.LoginRequest;
import com.lineless.auth_service.entity.User;
import com.lineless.auth_service.entity.UserStatus;
import com.lineless.auth_service.exception.EmailNotVerifiedException;
import com.lineless.auth_service.exception.InvalidCredentialsException;
import com.lineless.auth_service.exception.RateLimitExceededException;
import com.lineless.auth_service.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Locale;

@Service
@RequiredArgsConstructor
public class LoginService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final LoginAttemptRateLimitService loginAttemptRateLimitService;

    @Transactional(readOnly = true)
    public User login(LoginRequest request, String clientIp) {

        String normalizedEmail =
                request.email().trim().toLowerCase(Locale.ROOT);

        // 1. Check whether login attempts are allowed.
        boolean allowed = loginAttemptRateLimitService.isAllowed(
                normalizedEmail, clientIp);

        if (!allowed) {
            throw new RateLimitExceededException();
        }

        // 2. Find the user.
        User user = userRepository.findByEmail(normalizedEmail)
                .orElse(null);

        // 3. Unknown email: record a failed attempt.
        if (user == null) {
            loginAttemptRateLimitService.recordFailedAttempt(
                    normalizedEmail, clientIp);

            throw new InvalidCredentialsException();
        }

        // 4. Check password.
        if (user.getPasswordHash() == null ||
                !passwordEncoder.matches(
                        request.password(), user.getPasswordHash())) {

            loginAttemptRateLimitService.recordFailedAttempt(
                    normalizedEmail, clientIp);

            throw new InvalidCredentialsException();
        }

        // 5. Correct password: reset this email's failed attempts.
        loginAttemptRateLimitService.resetEmailAttempts(normalizedEmail);

        // 6. Check account status.
        if (user.getStatus() != UserStatus.ACTIVE) {
            throw new InvalidCredentialsException();
        }

        // 7. Check email verification.
        if (!user.isEmailVerified()) {
            throw new EmailNotVerifiedException();
        }

        return user;
    }
}
