package com.lineless.auth_service.service;

import com.lineless.auth_service.exception.RateLimitInfrastructureException;
import io.github.bucket4j.Bucket;
import io.github.bucket4j.BucketConfiguration;
import io.github.bucket4j.distributed.proxy.ProxyManager;
import io.lettuce.core.RedisException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.Duration;

@Service
@RequiredArgsConstructor
public class RateLimitService {

    private final ProxyManager<String> proxyManager;

    public boolean tryConsume(
            String key,
            long capacity,
            Duration refillPeriod) {

        try {
            return getBucket(key, capacity, refillPeriod).tryConsume(1);
        } catch (RedisException exception) {
            throw new RateLimitInfrastructureException(exception);
        }
    }

    public boolean isAllowed(
            String key,
            long capacity,
            Duration refillPeriod) {

        try {
            return getBucket(key, capacity, refillPeriod)
                    .getAvailableTokens() > 0;
        } catch (RedisException exception) {
            throw new RateLimitInfrastructureException(exception);
        }
    }

    public void reset(
            String key,
            long capacity,
            Duration refillPeriod) {

        try {
            getBucket(key, capacity, refillPeriod).reset();
        } catch (RedisException exception) {
            throw new RateLimitInfrastructureException(exception);
        }
    }

    private Bucket getBucket(
            String key,
            long capacity,
            Duration refillPeriod) {

        BucketConfiguration configuration = BucketConfiguration.builder()
                .addLimit(limit -> limit
                        .capacity(capacity)
                        .refillGreedy(capacity, refillPeriod))
                .build();

        return proxyManager.getProxy(key, () -> configuration);
    }
}
