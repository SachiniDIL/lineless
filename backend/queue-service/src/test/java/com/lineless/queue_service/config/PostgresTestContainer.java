package com.lineless.queue_service.config;

import org.springframework.test.context.DynamicPropertyRegistry;
import org.testcontainers.containers.PostgreSQLContainer;

public final class PostgresTestContainer {

    private static final PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>("postgres:17-alpine")
                    .withDatabaseName("lineless_queue_test")
                    .withUsername("lineless_test")
                    .withPassword("lineless_test");

    static {
        POSTGRES.start();
    }

    private PostgresTestContainer() {
    }

    public static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }
}
