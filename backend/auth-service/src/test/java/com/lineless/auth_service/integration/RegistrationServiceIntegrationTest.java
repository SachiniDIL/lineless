package com.lineless.auth_service.integration;

import com.lineless.auth_service.dto.request.RegisterRequest;
import com.lineless.auth_service.entity.User;
import com.lineless.auth_service.repository.UserRepository;
import com.lineless.auth_service.service.RegistrationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.Optional;
import java.util.concurrent.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doAnswer;

@Testcontainers
@SpringBootTest
class RegistrationServiceIntegrationTest {

    @Container
    static PostgreSQLContainer<?> postgres =
            new PostgreSQLContainer<>("postgres:16-alpine")
                    .withDatabaseName("lineless_test")
                    .withUsername("test")
                    .withPassword("test");
    @Autowired
    private RegistrationService registrationService;
    @MockitoSpyBean
    private UserRepository userRepository;
    @Autowired
    private JdbcTemplate jdbcTemplate;

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
    }

    @BeforeEach
    void cleanDatabase() {
        userRepository.deleteAll();
    }

    @Test
    void shouldRegisterNewUserSuccessfully() {

        RegisterRequest request = new RegisterRequest(
                "Jane Smith",
                "jane@example.com",
                "Password123"
        );

        Optional<User> result = registrationService.register(request);

        assertThat(result).isPresent();
        assertThat(result.get().getEmail()).isEqualTo("jane@example.com");
        assertThat(userRepository.count()).isEqualTo(1);
    }

    @Test
    void shouldReturnEmptyWhenEmailAlreadyExists() {

        RegisterRequest firstRequest = new RegisterRequest(
                "Jane Smith",
                "jane@example.com",
                "Password123"
        );

        RegisterRequest secondRequest = new RegisterRequest(
                "Another Name",
                "jane@example.com",
                "Password456"
        );

        registrationService.register(firstRequest);

        Optional<User> result =
                registrationService.register(secondRequest);

        assertThat(result).isEmpty();
        assertThat(userRepository.count()).isEqualTo(1);
    }

    @Test
    void shouldEnforceEmailUniquenessAtDatabaseLevel() {

        RegisterRequest request = new RegisterRequest(
                "Jane Smith",
                "jane@example.com",
                "Password123"
        );

        User firstUser = registrationService.register(request)
                .orElseThrow();

        User duplicateUser = new User();
        duplicateUser.setName("Another Name");
        duplicateUser.setEmail(firstUser.getEmail());
        duplicateUser.setPasswordHash("another-password-hash");

        assertThatThrownBy(
                () -> userRepository.saveAndFlush(duplicateUser)
        ).isInstanceOf(DataIntegrityViolationException.class);

        assertThat(userRepository.count()).isEqualTo(1);
    }


    @Test
    void shouldPreventDuplicateUsersDuringConcurrentRegistration()
            throws Exception {

        String email = "concurrent@example.com";

        CountDownLatch bothRequestsCheckedEmail = new CountDownLatch(2);

        doAnswer(invocation -> {

            // Execute the actual database existence check using SQL.
            boolean exists = Boolean.TRUE.equals(
                    jdbcTemplate.queryForObject(
                            "SELECT EXISTS (SELECT 1 FROM users WHERE email = ?)",
                            Boolean.class,
                            email
                    )
            );

            // Signal that this request has completed its existence check.
            bothRequestsCheckedEmail.countDown();

            // Wait until both requests have completed their checks.
            if (!bothRequestsCheckedEmail.await(10, TimeUnit.SECONDS)) {
                throw new IllegalStateException(
                        "Both requests did not reach the email check."
                );
            }

            return exists;

        }).when(userRepository).existsByEmail(email);

        RegisterRequest request1 = new RegisterRequest(
                "Customer One",
                email,
                "Password123"
        );

        RegisterRequest request2 = new RegisterRequest(
                "Customer Two",
                email,
                "Password456"
        );

        ExecutorService executor = Executors.newFixedThreadPool(2);

        try {

            Future<Optional<User>> future1 =
                    executor.submit(() -> registrationService.register(request1));

            Future<Optional<User>> future2 =
                    executor.submit(() -> registrationService.register(request2));

            Optional<User> result1 = future1.get(15, TimeUnit.SECONDS);
            Optional<User> result2 = future2.get(15, TimeUnit.SECONDS);

            // Exactly one request should successfully register.
            assertThat(
                    result1.isPresent() ^ result2.isPresent()
            ).isTrue();

            // Only one user should exist in PostgreSQL.
            assertThat(userRepository.count()).isEqualTo(1);

            // The stored email must be the expected email.
            assertThat(userRepository.findByEmail(email)).isPresent();

        } finally {
            executor.shutdownNow();
        }
    }

}
