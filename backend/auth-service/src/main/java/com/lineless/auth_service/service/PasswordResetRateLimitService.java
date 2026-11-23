package com.lineless.auth_service.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.Locale;

@Service
@RequiredArgsConstructor
public class PasswordResetRateLimitService {

    private static final long EMAIL_CAPACITY = 3;
    private static final long IP_CAPACITY = 10;

    private static final Duration EMAIL_REFILL_PERIOD =
            Duration.ofMinutes(15);

    private static final Duration IP_REFILL_PERIOD =
            Duration.ofHours(1);

    private static final String EMAIL_KEY_PREFIX =
            "lineless:auth:rate-limit:password-reset:email:";

    private static final String IP_KEY_PREFIX =
            "lineless:auth:rate-limit:password-reset:ip:";

    private final RateLimitService rateLimitService;

    public boolean tryConsume(String email, String clientIp) {

        boolean emailAllowed = rateLimitService.tryConsume(
                getEmailKey(email),
                EMAIL_CAPACITY,
                EMAIL_REFILL_PERIOD
        );

        if (!emailAllowed) {
            return false;
        }

        return rateLimitService.tryConsume(
                getIpKey(clientIp),
                IP_CAPACITY,
                IP_REFILL_PERIOD
        );
    }

    private String getEmailKey(String email) {
        String normalizedEmail =
                email.trim().toLowerCase(Locale.ROOT);

        return EMAIL_KEY_PREFIX + hash(normalizedEmail);
    }

    private String getIpKey(String clientIp) {
        return IP_KEY_PREFIX + hash(clientIp);
    }

    private String hash(String value) {
        try {
            var digest = java.security.MessageDigest.getInstance("SHA-256");

            byte[] hashBytes = digest.digest(
                    value.getBytes(java.nio.charset.StandardCharsets.UTF_8)
            );

            return java.util.HexFormat.of().formatHex(hashBytes);

        } catch (java.security.NoSuchAlgorithmException exception) {
            throw new IllegalStateException(
                    "Unable to hash rate limit key",
                    exception
            );
        }
    }
}
