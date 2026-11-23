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

        when(rateLimitService.tryConsume(
                anyString(), eq(3L), any(Duration.class)))
                .thenReturn(true);

        when(rateLimitService.tryConsume(
                anyString(), eq(5L), any(Duration.class)))
                .thenReturn(true);

        assertTrue(registrationRateLimitService.tryConsume(
                EMAIL, CLIENT_IP));

        verify(rateLimitService).tryConsume(
                startsWith("lineless:auth:rate-limit:register:email:"),
                eq(3L),
                eq(Duration.ofHours(1))
        );

        verify(rateLimitService).tryConsume(
                startsWith("lineless:auth:rate-limit:register:ip:"),
                eq(5L),
                eq(Duration.ofHours(1))
        );
    }

    @Test
    void blocksRegistrationWhenEmailLimitIsReached() {

        when(rateLimitService.tryConsume(
                anyString(), eq(3L), any(Duration.class)))
                .thenReturn(false);

        when(rateLimitService.tryConsume(
                anyString(), eq(5L), any(Duration.class)))
                .thenReturn(true);

        assertFalse(registrationRateLimitService.tryConsume(
                EMAIL, CLIENT_IP));

        // Both limits should still be checked and consumed independently.
        verify(rateLimitService).tryConsume(
                startsWith("lineless:auth:rate-limit:register:email:"),
                eq(3L),
                eq(Duration.ofHours(1))
        );

        verify(rateLimitService).tryConsume(
                startsWith("lineless:auth:rate-limit:register:ip:"),
                eq(5L),
                eq(Duration.ofHours(1))
        );
    }

    @Test
    void blocksRegistrationWhenIpLimitIsReached() {

        when(rateLimitService.tryConsume(
                anyString(), eq(3L), any(Duration.class)))
                .thenReturn(true);

        when(rateLimitService.tryConsume(
                anyString(), eq(5L), any(Duration.class)))
                .thenReturn(false);

        assertFalse(registrationRateLimitService.tryConsume(
                EMAIL, CLIENT_IP));
    }

    @Test
    void normalizesEmailBeforeCreatingRateLimitKey() {

        when(rateLimitService.tryConsume(
                anyString(), anyLong(), any(Duration.class)))
                .thenReturn(true);

        registrationRateLimitService.tryConsume(
                "  JANE@EXAMPLE.COM  ", CLIENT_IP);

        verify(rateLimitService).tryConsume(
                startsWith("lineless:auth:rate-limit:register:email:"),
                eq(3L),
                eq(Duration.ofHours(1))
        );
    }
}
