package com.lineless.auth_service.config;

import io.lettuce.core.RedisClient;
import io.lettuce.core.RedisURI;
import io.lettuce.core.api.StatefulRedisConnection;
import io.lettuce.core.codec.ByteArrayCodec;
import io.lettuce.core.codec.RedisCodec;
import io.lettuce.core.codec.StringCodec;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RedisConfig {

    @Bean(destroyMethod = "shutdown")
    public RedisClient redisClient(
            @Value("${spring.data.redis.host}") String host,
            @Value("${spring.data.redis.port}") int port,
            @Value("${spring.data.redis.password}") String password) {

        RedisURI redisUri = RedisURI.Builder
                .redis(host, port)
                .withPassword(password.toCharArray())
                .build();

        return RedisClient.create(redisUri);
    }

    @Bean(destroyMethod = "close")
    public StatefulRedisConnection<String, byte[]> redisConnection(
            RedisClient redisClient) {

        return redisClient.connect(
                RedisCodec.of(
                        StringCodec.UTF8,
                        ByteArrayCodec.INSTANCE
                )
        );
    }
}