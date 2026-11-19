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

    public boolean tryConsume(
            String key,
            long capacity,
            Duration refillPeriod) {

        BucketConfiguration configuration = BucketConfiguration.builder()
                .addLimit(limit -> limit
                        .capacity(capacity)
                        .refillGreedy(capacity, refillPeriod))
                .build();

        Bucket bucket = proxyManager.getProxy(
                key,
                () -> configuration
        );

        return bucket.tryConsume(1);
    }
}