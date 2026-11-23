package com.lineless.auth_service.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class LoginAttemptRateLimitServiceTest {

    @Mock
    private RateLimitService rateLimitService;

    private LoginAttemptRateLimitService loginAttemptRateLimitService;

    private static final String EMAIL = "test@example.com";
    private static final String CLIENT_IP = "192.168.1.10";

    @BeforeEach
    void setUp() {
        loginAttemptRateLimitService =
                new LoginAttemptRateLimitService(rateLimitService);
    }

    @Test
    void allowsLoginWhenBothLimitsHaveAvailableTokens() {
        when(rateLimitService.isAllowed(
                anyString(), eq(5L), any(Duration.class)))
                .thenReturn(true);

        when(rateLimitService.isAllowed(
                anyString(), eq(20L), any(Duration.class)))
                .thenReturn(true);

        assertTrue(loginAttemptRateLimitService.isAllowed(
                EMAIL, CLIENT_IP));
    }

    @Test
    void blocksLoginWhenEmailLimitIsReached() {
        when(rateLimitService.isAllowed(
                anyString(), eq(5L), any(Duration.class)))
                .thenReturn(false);

        when(rateLimitService.isAllowed(
                anyString(), eq(20L), any(Duration.class)))
                .thenReturn(true);

        assertFalse(loginAttemptRateLimitService.isAllowed(
                EMAIL, CLIENT_IP));
    }

    @Test
    void blocksLoginWhenIpLimitIsReached() {
        when(rateLimitService.isAllowed(
                anyString(), eq(5L), any(Duration.class)))
                .thenReturn(true);

        when(rateLimitService.isAllowed(
                anyString(), eq(20L), any(Duration.class)))
                .thenReturn(false);

        assertFalse(loginAttemptRateLimitService.isAllowed(
                EMAIL, CLIENT_IP));
    }

    @Test
    void recordsFailedAttemptForEmailAndIp() {
        loginAttemptRateLimitService.recordFailedAttempt(
                EMAIL, CLIENT_IP);

        verify(rateLimitService).tryConsume(
                startsWith("lineless:auth:rate-limit:login:email:"),
                eq(5L),
                eq(Duration.ofMinutes(15)));

        verify(rateLimitService).tryConsume(
                startsWith("lineless:auth:rate-limit:login:ip:"),
                eq(20L),
                eq(Duration.ofMinutes(15)));
    }

    @Test
    void resetsOnlyEmailAttempts() {
        loginAttemptRateLimitService.resetEmailAttempts(EMAIL);

        verify(rateLimitService).reset(
                startsWith("lineless:auth:rate-limit:login:email:"),
                eq(5L),
                eq(Duration.ofMinutes(15)));

        verifyNoMoreInteractions(rateLimitService);
    }

    @Test
    void normalizesEmailBeforeCreatingRateLimitKey() {
        loginAttemptRateLimitService.recordFailedAttempt(
                "  TEST@EXAMPLE.COM  ", CLIENT_IP);

        verify(rateLimitService).tryConsume(
                startsWith("lineless:auth:rate-limit:login:email:"),
                eq(5L),
                eq(Duration.ofMinutes(15)));
    }
}
