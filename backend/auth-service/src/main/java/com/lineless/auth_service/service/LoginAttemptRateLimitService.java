package com.lineless.auth_service.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.HexFormat;
import java.util.Locale;

@Service
@RequiredArgsConstructor
public class LoginAttemptRateLimitService {

    private static final long EMAIL_CAPACITY = 5;
    private static final long IP_CAPACITY = 20;
    private static final Duration REFILL_PERIOD = Duration.ofMinutes(15);

    private static final String EMAIL_KEY_PREFIX =
            "lineless:auth:rate-limit:login:email:";

    private static final String IP_KEY_PREFIX =
            "lineless:auth:rate-limit:login:ip:";

    private final RateLimitService rateLimitService;

    public boolean isAllowed(String email, String clientIp) {
        String emailKey = getEmailKey(email);
        String ipKey = getIpKey(clientIp);

        boolean emailAllowed = rateLimitService.isAllowed(
                emailKey, EMAIL_CAPACITY, REFILL_PERIOD);

        boolean ipAllowed = rateLimitService.isAllowed(
                ipKey, IP_CAPACITY, REFILL_PERIOD);

        return emailAllowed && ipAllowed;
    }


    public boolean recordFailedAttempt(String email, String clientIp) {

        boolean emailAllowed = rateLimitService.tryConsume(
                getEmailKey(email),
                EMAIL_CAPACITY,
                REFILL_PERIOD
        );

        boolean ipAllowed = rateLimitService.tryConsume(
                getIpKey(clientIp),
                IP_CAPACITY,
                REFILL_PERIOD
        );

        return emailAllowed && ipAllowed;
    }

    public void resetEmailAttempts(String email) {
        rateLimitService.reset(
                getEmailKey(email), EMAIL_CAPACITY, REFILL_PERIOD);
    }

    private String getEmailKey(String email) {
        String normalizedEmail = email.trim().toLowerCase(Locale.ROOT);

        return EMAIL_KEY_PREFIX + hash(normalizedEmail);
    }

    private String getIpKey(String clientIp) {
        return IP_KEY_PREFIX + hash(clientIp);
    }

    private String hash(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(
                    value.getBytes(StandardCharsets.UTF_8));

            return HexFormat.of().formatHex(hash);

        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException(
                    "SHA-256 algorithm is unavailable", exception);
        }
    }
}
