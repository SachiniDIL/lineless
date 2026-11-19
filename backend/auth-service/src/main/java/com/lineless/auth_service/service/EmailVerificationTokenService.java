package com.lineless.auth_service.service;

import com.lineless.auth_service.entity.EmailVerificationToken;
import com.lineless.auth_service.entity.User;
import com.lineless.auth_service.repository.EmailVerificationTokenRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Base64;
import java.util.HexFormat;

@Service
@RequiredArgsConstructor
public class EmailVerificationTokenService {

    private static final int TOKEN_BYTE_LENGTH = 32;
    private static final long TOKEN_EXPIRATION_MINUTES = 15;

    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    private final EmailVerificationTokenRepository tokenRepository;

    @Transactional
    public String createVerificationToken(User user) {

        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);

        // Invalidate previous unused verification tokens.
        tokenRepository.invalidateUnusedTokensForUser(
                user.getId(),
                now
        );

        // Generate a secure random token.
        byte[] randomBytes = new byte[TOKEN_BYTE_LENGTH];
        SECURE_RANDOM.nextBytes(randomBytes);

        String rawToken = Base64.getUrlEncoder()
                .withoutPadding()
                .encodeToString(randomBytes);

        // Store only the SHA-256 hash, never the original token.
        String tokenHash = hashTokenValue(rawToken);

        EmailVerificationToken verificationToken =
                new EmailVerificationToken();

        verificationToken.setUser(user);
        verificationToken.setTokenHash(tokenHash);
        verificationToken.setExpiresAt(
                now.plusMinutes(TOKEN_EXPIRATION_MINUTES)
        );

        tokenRepository.save(verificationToken);

        // The original token will be sent to the user's email.
        return rawToken;
    }

    public static String hashTokenValue(String rawToken){
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");

            byte[] hashedBytes = digest.digest(
                    rawToken.getBytes(java.nio.charset.StandardCharsets.UTF_8)
            );

            return HexFormat.of().formatHex(hashedBytes);

        } catch (java.security.NoSuchAlgorithmException exception) {
            throw new IllegalStateException(
                    "SHA-256 algorithm is not available",
                    exception
            );
        }
    }
}
