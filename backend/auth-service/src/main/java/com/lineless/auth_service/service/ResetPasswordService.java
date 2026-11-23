package com.lineless.auth_service.service;

import com.lineless.auth_service.dto.request.ResetPasswordRequest;
import com.lineless.auth_service.entity.PasswordResetToken;
import com.lineless.auth_service.entity.User;
import com.lineless.auth_service.entity.UserStatus;
import com.lineless.auth_service.exception.InvalidPasswordResetTokenException;
import com.lineless.auth_service.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;

@Service
@RequiredArgsConstructor
public class ResetPasswordService {

    private final PasswordResetTokenService passwordResetTokenService;
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final RefreshTokenService refreshTokenService;

    @Transactional
    public void resetPassword(ResetPasswordRequest request) {

        PasswordResetToken resetToken =
                passwordResetTokenService.validatePasswordResetToken(
                        request.token()
                );

        User user = resetToken.getUser();

        // Prevent inactive accounts from resetting their passwords
        if (user.getStatus() != UserStatus.ACTIVE) {
            throw new InvalidPasswordResetTokenException(
                    "Invalid or expired password reset link."
            );
        }

        user.setPasswordHash(
                passwordEncoder.encode(request.newPassword())
        );

        userRepository.save(user);

        resetToken.setUsedAt(OffsetDateTime.now());

        refreshTokenService.revokeAllUserSessions(user.getId());
    }
}