package com.lineless.auth_service.service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.OffsetDateTime;
import java.util.Base64;
import java.util.UUID;

import com.lineless.auth_service.dto.response.RefreshTokenResult;
import com.lineless.auth_service.entity.UserStatus;
import com.lineless.auth_service.exception.RefreshTokenReuseException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.lineless.auth_service.entity.RefreshToken;
import com.lineless.auth_service.entity.User;
import com.lineless.auth_service.exception.InvalidRefreshTokenException;
import com.lineless.auth_service.repository.RefreshTokenRepository;

@Service
public class RefreshTokenService {

    private final RefreshTokenRepository refreshTokenRepository;
    private final long refreshTokenExpiration;
    private final SecureRandom secureRandom = new SecureRandom();

    public RefreshTokenService(
            RefreshTokenRepository refreshTokenRepository,
            @Value("${app.jwt.refresh-token-expiration}") long refreshTokenExpiration) {

        this.refreshTokenRepository = refreshTokenRepository;
        this.refreshTokenExpiration = refreshTokenExpiration;
    }


    @Transactional
    public String createRefreshToken(User user) {
        // Convert the bytes into a URL-safe token.
        String rawToken = generateRawToken();

        // Hash the raw token before storing it.
        String tokenHash = hashToken(rawToken);

        RefreshToken refreshToken = new RefreshToken();
        refreshToken.setUser(user);
        refreshToken.setTokenHash(tokenHash);
        refreshToken.setFamilyId(UUID.randomUUID());
        refreshToken.setExpiresAt(
                OffsetDateTime.now().plusSeconds(refreshTokenExpiration)
        );

        refreshTokenRepository.save(refreshToken);

        // Return the original token, never the hash.
        return rawToken;
    }

    @Transactional(noRollbackFor = {
            RefreshTokenReuseException.class,
            InvalidRefreshTokenException.class
    })
    public RefreshTokenResult rotateRefreshToken(String rawToken) {

        if (rawToken == null || rawToken.isBlank()) {
            throw new InvalidRefreshTokenException(
                    "Invalid refresh token"
            );
        }

        String tokenHash = hashToken(rawToken);

        RefreshToken existingToken = refreshTokenRepository
                .findByTokenHashForUpdate(tokenHash)
                .orElseThrow(() -> new InvalidRefreshTokenException(
                        "Invalid refresh token"
                ));

        OffsetDateTime now = OffsetDateTime.now();

        // The token was already used or revoked.
        if (existingToken.getRevokedAt() != null) {

            refreshTokenRepository.revokeActiveTokensByFamilyId(
                    existingToken.getFamilyId(),
                    now
            );

            throw new RefreshTokenReuseException(
                    "Refresh token reuse detected"
            );
        }

        // The token has expired.
        if (!existingToken.getExpiresAt().isAfter(now)) {
            throw new InvalidRefreshTokenException(
                    "Refresh token has expired"
            );
        }

        // Do not allow inactive users to refresh their sessions.
        if (existingToken.getUser().getStatus() != UserStatus.ACTIVE) {
            refreshTokenRepository.revokeActiveTokensByFamilyId(
                    existingToken.getFamilyId(),
                    now
            );

            throw new InvalidRefreshTokenException(
                    "User account is inactive"
            );
        }

        // Generate a new token in the same family.
        String newRawToken = generateRawToken();

        RefreshToken newToken = new RefreshToken();
        newToken.setUser(existingToken.getUser());
        newToken.setTokenHash(hashToken(newRawToken));
        newToken.setFamilyId(existingToken.getFamilyId());
        newToken.setExpiresAt(
                now.plusSeconds(refreshTokenExpiration)
        );

        RefreshToken savedNewToken =
                refreshTokenRepository.save(newToken);

        // Revoke the old token and link it to the replacement.
        existingToken.setRevokedAt(now);
        existingToken.setReplacedBy(savedNewToken.getId());

        refreshTokenRepository.save(existingToken);

        return new RefreshTokenResult(
                newRawToken,
                existingToken.getUser()
        );
    }

    @Transactional
    public void revokeRefreshToken(String rawToken) {

        if (rawToken == null || rawToken.isBlank()) {
            return;
        }

        String tokenHash = hashToken(rawToken);

        refreshTokenRepository.findByTokenHashForUpdate(tokenHash)
                .ifPresent(refreshToken -> {

                    if (refreshToken.getRevokedAt() == null) {
                        refreshToken.setRevokedAt(OffsetDateTime.now());
                        refreshTokenRepository.save(refreshToken);
                    }
                });
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
                    "Unable to hash refresh token",
                    exception
            );
        }
    }

    private String generateRawToken() {
        byte[] randomBytes = new byte[32];
        secureRandom.nextBytes(randomBytes);

        return Base64.getUrlEncoder()
                .withoutPadding()
                .encodeToString(randomBytes);
    }
}