package com.lineless.auth_service.config;

import io.github.bucket4j.distributed.ExpirationAfterWriteStrategy;
import io.github.bucket4j.distributed.proxy.ProxyManager;
import io.github.bucket4j.redis.lettuce.Bucket4jLettuce;
import io.lettuce.core.api.StatefulRedisConnection;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;

@Configuration
public class Bucket4jConfig {

    @Bean
    public ProxyManager<String> proxyManager(
            StatefulRedisConnection<String, byte[]> redisConnection) {

        return Bucket4jLettuce.casBasedBuilder(redisConnection)
                .maxRetries(5)
                .requestTimeout(Duration.ofSeconds(2))
                .expirationAfterWrite(
                        ExpirationAfterWriteStrategy
                                .basedOnTimeForRefillingBucketUpToMax(Duration.ofHours(1))
                )
                .build();
    }
}