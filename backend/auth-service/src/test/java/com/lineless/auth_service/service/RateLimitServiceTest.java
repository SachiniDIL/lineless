package com.lineless.auth_service.service;

import com.lineless.auth_service.exception.RateLimitInfrastructureException;
import io.github.bucket4j.distributed.BucketProxy;
import io.github.bucket4j.distributed.proxy.ProxyManager;
import io.lettuce.core.RedisException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class RateLimitServiceTest {

    private static final String KEY = "test:rate-limit";
    private static final long CAPACITY = 5;
    private static final Duration REFILL_PERIOD = Duration.ofMinutes(15);

    @Mock
    private ProxyManager<String> proxyManager;

    @Mock
    private BucketProxy bucketProxy;

    private RateLimitService rateLimitService;

    @BeforeEach
    void setUp() {
        rateLimitService = new RateLimitService(proxyManager);
    }

    @Test
    void consumesTokenWhenBucketHasAvailableTokens() {

        doReturn(bucketProxy)
                .when(proxyManager)
                .getProxy(eq(KEY), any());

        when(bucketProxy.tryConsume(1))
                .thenReturn(true);

        boolean result = rateLimitService.tryConsume(
                KEY, CAPACITY, REFILL_PERIOD
        );

        assertThat(result).isTrue();

        verify(bucketProxy).tryConsume(1);
    }

    @Test
    void returnsFalseWhenBucketHasNoAvailableTokens() {

        doReturn(bucketProxy)
                .when(proxyManager)
                .getProxy(eq(KEY), any());

        when(bucketProxy.tryConsume(1))
                .thenReturn(false);

        boolean result = rateLimitService.tryConsume(
                KEY, CAPACITY, REFILL_PERIOD
        );

        assertThat(result).isFalse();

        verify(bucketProxy).tryConsume(1);
    }

    @Test
    void checksWhetherTokensAreAvailable() {

        doReturn(bucketProxy)
                .when(proxyManager)
                .getProxy(eq(KEY), any());

        when(bucketProxy.getAvailableTokens())
                .thenReturn(3L);

        boolean result = rateLimitService.isAllowed(
                KEY, CAPACITY, REFILL_PERIOD
        );

        assertThat(result).isTrue();

        verify(bucketProxy).getAvailableTokens();
    }

    @Test
    void returnsFalseWhenNoTokensAreAvailable() {

        doReturn(bucketProxy)
                .when(proxyManager)
                .getProxy(eq(KEY), any());

        when(bucketProxy.getAvailableTokens())
                .thenReturn(0L);

        boolean result = rateLimitService.isAllowed(
                KEY, CAPACITY, REFILL_PERIOD
        );

        assertThat(result).isFalse();
    }

    @Test
    void resetsBucket() {

        doReturn(bucketProxy)
                .when(proxyManager)
                .getProxy(eq(KEY), any());

        rateLimitService.reset(KEY, CAPACITY, REFILL_PERIOD);

        verify(bucketProxy).reset();
    }

    @Test
    void throwsInfrastructureExceptionWhenTryConsumeFails() {

        when(proxyManager.getProxy(eq(KEY), any()))
                .thenThrow(new RedisException("Redis unavailable"));

        assertThatThrownBy(() ->
                rateLimitService.tryConsume(KEY, CAPACITY, REFILL_PERIOD)
        ).isInstanceOf(RateLimitInfrastructureException.class);
    }

    @Test
    void throwsInfrastructureExceptionWhenIsAllowedFails() {

        when(proxyManager.getProxy(eq(KEY), any()))
                .thenThrow(new RedisException("Redis unavailable"));

        assertThatThrownBy(() ->
                rateLimitService.isAllowed(KEY, CAPACITY, REFILL_PERIOD)
        ).isInstanceOf(RateLimitInfrastructureException.class);
    }

    @Test
    void throwsInfrastructureExceptionWhenResetFails() {

        when(proxyManager.getProxy(eq(KEY), any()))
                .thenThrow(new RedisException("Redis unavailable"));

        assertThatThrownBy(() ->
                rateLimitService.reset(KEY, CAPACITY, REFILL_PERIOD)
        ).isInstanceOf(RateLimitInfrastructureException.class);
    }
}
