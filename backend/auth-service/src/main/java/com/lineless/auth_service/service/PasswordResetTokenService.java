package com.lineless.auth_service.service;

import com.lineless.auth_service.entity.PasswordResetToken;
import com.lineless.auth_service.entity.User;
import com.lineless.auth_service.exception.InvalidPasswordResetTokenException;
import com.lineless.auth_service.repository.PasswordResetTokenRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.OffsetDateTime;
import java.util.Base64;

@Service
@RequiredArgsConstructor
public class PasswordResetTokenService {

    private static final long TOKEN_EXPIRATION_MINUTES = 15;

    private final PasswordResetTokenRepository passwordResetTokenRepository;

    private final SecureRandom secureRandom = new SecureRandom();

    @Transactional
    public String createPasswordResetToken(User user) {

        passwordResetTokenRepository.invalidateUnusedTokensByUserId(
                user.getId(),
                OffsetDateTime.now()
        );

        String rawToken = generateRawToken();

        PasswordResetToken passwordResetToken = new PasswordResetToken();
        passwordResetToken.setUser(user);
        passwordResetToken.setTokenHash(hashToken(rawToken));
        passwordResetToken.setExpiresAt(
                OffsetDateTime.now().plusMinutes(TOKEN_EXPIRATION_MINUTES)
        );

        passwordResetTokenRepository.save(passwordResetToken);

        return rawToken;
    }

    @Transactional
    public PasswordResetToken validatePasswordResetToken(String rawToken) {

        if (rawToken == null || rawToken.isBlank()) {
            throw new InvalidPasswordResetTokenException(
                    "Invalid or expired password reset link."
            );
        }

        String tokenHash = hashToken(rawToken);

        PasswordResetToken resetToken = passwordResetTokenRepository
                .findByTokenHashForUpdate(tokenHash)
                .orElseThrow(() -> new InvalidPasswordResetTokenException(
                        "Invalid or expired password reset link."
                ));

        if (resetToken.getUsedAt() != null) {
            throw new InvalidPasswordResetTokenException(
                    "Invalid or expired password reset link."
            );
        }

        if (!resetToken.getExpiresAt().isAfter(OffsetDateTime.now())) {
            throw new InvalidPasswordResetTokenException(
                    "Invalid or expired password reset link."
            );
        }

        return resetToken;
    }

    private String generateRawToken() {

        byte[] randomBytes = new byte[32];
        secureRandom.nextBytes(randomBytes);

        return Base64.getUrlEncoder()
                .withoutPadding()
                .encodeToString(randomBytes);
    }

    private String hashToken(String token) {

        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");

            byte[] hashBytes = digest.digest(
                    token.getBytes(StandardCharsets.UTF_8)
            );

            return java.util.HexFormat.of().formatHex(hashBytes);

        } catch (Exception exception) {
            throw new IllegalStateException(
                    "Unable to hash password reset token",
                    exception
            );
        }
    }
}