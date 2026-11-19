package com.lineless.auth_service.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class RegistrationRateLimitServiceTest {

    private static final String EMAIL = "jane@example.com";
    private static final String CLIENT_IP = "192.168.1.10";
    @Mock
    private RateLimitService rateLimitService;
    private RegistrationRateLimitService registrationRateLimitService;

    @BeforeEach
    void setUp() {
        registrationRateLimitService =
                new RegistrationRateLimitService(rateLimitService);
    }

    @Test
    void allowsRegistrationWhenBothLimitsHaveAvailableTokens() {
        when(rateLimitService.isAllowed(
                anyString(), eq(3L), any(Duration.class)))
                .thenReturn(true);

        when(rateLimitService.isAllowed(
                anyString(), eq(5L), any(Duration.class)))
                .thenReturn(true);

        assertTrue(registrationRateLimitService.isAllowed(
                EMAIL, CLIENT_IP));
    }

    @Test
    void blocksRegistrationWhenEmailLimitIsReached() {
        when(rateLimitService.isAllowed(
                anyString(), eq(3L), any(Duration.class)))
                .thenReturn(false);

        when(rateLimitService.isAllowed(
                anyString(), eq(5L), any(Duration.class)))
                .thenReturn(true);

        assertFalse(registrationRateLimitService.isAllowed(
                EMAIL, CLIENT_IP));
    }

    @Test
    void blocksRegistrationWhenIpLimitIsReached() {
        when(rateLimitService.isAllowed(
                anyString(), eq(3L), any(Duration.class)))
                .thenReturn(true);

        when(rateLimitService.isAllowed(
                anyString(), eq(5L), any(Duration.class)))
                .thenReturn(false);

        assertFalse(registrationRateLimitService.isAllowed(
                EMAIL, CLIENT_IP));
    }

    @Test
    void recordsRegistrationAttemptForEmailAndIp() {
        registrationRateLimitService.recordAttempt(
                EMAIL, CLIENT_IP);

        verify(rateLimitService).tryConsume(
                startsWith("lineless:auth:rate-limit:register:email:"),
                eq(3L),
                eq(Duration.ofHours(1)));

        verify(rateLimitService).tryConsume(
                startsWith("lineless:auth:rate-limit:register:ip:"),
                eq(5L),
                eq(Duration.ofHours(1)));
    }

    @Test
    void normalizesEmailBeforeCreatingRateLimitKey() {
        registrationRateLimitService.recordAttempt(
                "  JANE@EXAMPLE.COM  ", CLIENT_IP);

        verify(rateLimitService).tryConsume(
                startsWith("lineless:auth:rate-limit:register:email:"),
                eq(3L),
                eq(Duration.ofHours(1)));
    }
}
