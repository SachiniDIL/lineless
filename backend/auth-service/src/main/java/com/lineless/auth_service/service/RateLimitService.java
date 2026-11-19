
package com.lineless.auth_service.service;

import io.github.bucket4j.Bucket;
import io.github.bucket4j.BucketConfiguration;
import io.github.bucket4j.distributed.proxy.ProxyManager;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.Duration;

@Service
@RequiredArgsConstructor
public class RateLimitService {

    private final ProxyManager<String> proxyManager;

    public boolean tryConsume(String key, long capacity, Duration refillPeriod) {
        return getBucket(key, capacity, refillPeriod).tryConsume(1);
    }

    public boolean isAllowed(String key, long capacity, Duration refillPeriod) {
        return getBucket(key, capacity, refillPeriod)
                .getAvailableTokens() > 0;
    }

    public void reset(String key, long capacity, Duration refillPeriod) {
        getBucket(key, capacity, refillPeriod).reset();
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
