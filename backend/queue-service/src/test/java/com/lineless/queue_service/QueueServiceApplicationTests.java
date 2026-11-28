package com.lineless.queue_service;

import com.lineless.queue_service.config.PostgresTestContainer;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

@SpringBootTest
@ActiveProfiles("test")
class QueueServiceApplicationTests {

	@DynamicPropertySource
	static void configureProperties(DynamicPropertyRegistry registry) {
		PostgresTestContainer.configureProperties(registry);
	}

	@Test
	void contextLoads() {
	}
}
