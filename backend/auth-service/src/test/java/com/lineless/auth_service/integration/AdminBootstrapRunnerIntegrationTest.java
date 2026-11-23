package com.lineless.auth_service.integration;

import com.lineless.auth_service.config.AdminBootstrapRunner;
import com.lineless.auth_service.entity.User;
import com.lineless.auth_service.entity.UserRole;
import com.lineless.auth_service.entity.UserStatus;
import com.lineless.auth_service.repository.UserRepository;
import org.hibernate.exception.ConstraintViolationException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.Optional;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

@SpringBootTest
@Testcontainers
class AdminBootstrapRunnerIntegrationTest {

    private static final String ADMIN_EMAIL = "concurrent-admin@example.com";

    @Container
    static PostgreSQLContainer<?> postgres =
            new PostgreSQLContainer<>("postgres:16-alpine")
                    .withDatabaseName("lineless_test")
                    .withUsername("test")
                    .withPassword("test");
    @Autowired
    private PasswordEncoder passwordEncoder;
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
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void shouldHandleConcurrentAdminProvisioningSafely() throws Exception {
        CountDownLatch bothInitialChecksCompleted = new CountDownLatch(2);
        AtomicInteger initialFindCount = new AtomicInteger();

        doAnswer(invocation -> {
            String email = invocation.getArgument(0);
            Optional<User> result = findByEmailThroughJdbc(email);

            if (initialFindCount.incrementAndGet() <= 2) {
                bothInitialChecksCompleted.countDown();
                if (!bothInitialChecksCompleted.await(10, TimeUnit.SECONDS)) {
                    throw new IllegalStateException(
                            "Both admin provisioning attempts did not complete their initial email checks");
                }
            }

            return result;
        }).when(userRepository).findByEmail(ADMIN_EMAIL);

        AdminBootstrapRunner firstRunner = new AdminBootstrapRunner(
                userRepository,
                passwordEncoder,
                true,
                "Concurrent Admin One",
                ADMIN_EMAIL,
                "WinningPassword123"
        );

        AdminBootstrapRunner secondRunner = new AdminBootstrapRunner(
                userRepository,
                passwordEncoder,
                true,
                "Concurrent Admin Two",
                ADMIN_EMAIL.toUpperCase(),
                "OtherWinningPassword456"
        );

        ExecutorService executor = Executors.newFixedThreadPool(2);

        try {
            Future<?> firstAttempt = executor.submit(() -> firstRunner.run());
            Future<?> secondAttempt = executor.submit(() -> secondRunner.run());

            firstAttempt.get(15, TimeUnit.SECONDS);
            secondAttempt.get(15, TimeUnit.SECONDS);
        } finally {
            executor.shutdownNow();
        }

        assertThat(userRepository.count()).isEqualTo(1);

        User savedAdmin = userRepository.findByEmail(ADMIN_EMAIL).orElseThrow();
        assertThat(savedAdmin.getRole()).isEqualTo(UserRole.ADMIN);
        assertThat(savedAdmin.getStatus()).isEqualTo(UserStatus.ACTIVE);
        assertThat(savedAdmin.getName()).isIn(
                "Concurrent Admin One",
                "Concurrent Admin Two"
        );

        if ("Concurrent Admin One".equals(savedAdmin.getName())) {
            assertThat(passwordEncoder.matches(
                    "WinningPassword123",
                    savedAdmin.getPasswordHash()
            )).isTrue();
        } else {
            assertThat(passwordEncoder.matches(
                    "OtherWinningPassword456",
                    savedAdmin.getPasswordHash()
            )).isTrue();
        }

        User duplicate = new User();
        duplicate.setName("Duplicate Admin");
        duplicate.setEmail(ADMIN_EMAIL);
        duplicate.setPasswordHash("duplicate-password");
        duplicate.setRole(UserRole.ADMIN);
        duplicate.setStatus(UserStatus.ACTIVE);

        assertThatThrownBy(() -> userRepository.saveAndFlush(duplicate))
                .isInstanceOf(DataIntegrityViolationException.class)
                .satisfies(exception -> assertThat(
                        findConstraintName(
                                (DataIntegrityViolationException) exception
                        )
                ).isEqualTo("users_email_key"));

        verify(userRepository, atLeast(3)).findByEmail(ADMIN_EMAIL);
        verify(userRepository, atLeast(2))
                .saveAndFlush(org.mockito.ArgumentMatchers.any(User.class));
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void shouldNotPromoteExistingUserDuringAdminProvisioning() {
        User existingUser = new User();
        existingUser.setName("Existing Customer");
        existingUser.setEmail(ADMIN_EMAIL);
        existingUser.setPasswordHash("existing-user-hash");
        existingUser.setRole(UserRole.USER);
        existingUser.setStatus(UserStatus.ACTIVE);
        User savedUser = userRepository.saveAndFlush(existingUser);

        AdminBootstrapRunner runner = new AdminBootstrapRunner(
                userRepository,
                passwordEncoder,
                true,
                "Configured Admin",
                ADMIN_EMAIL,
                "ConfiguredPassword123"
        );

        runner.run();

        User unchangedUser = userRepository.findById(savedUser.getId()).orElseThrow();
        assertThat(unchangedUser.getRole()).isEqualTo(UserRole.USER);
        assertThat(unchangedUser.getName()).isEqualTo("Existing Customer");
        assertThat(unchangedUser.getPasswordHash()).isEqualTo("existing-user-hash");
    }

    private String findConstraintName(DataIntegrityViolationException exception) {
        for (Throwable cause = exception; cause != null; cause = cause.getCause()) {
            if (cause instanceof ConstraintViolationException constraintViolation) {
                return constraintViolation.getConstraintName();
            }
        }
        return null;
    }

    private Optional<User> findByEmailThroughJdbc(String email) {
        java.util.UUID id;
        try {
            id = jdbcTemplate.queryForObject(
                    "SELECT id FROM users WHERE email = ?",
                    java.util.UUID.class,
                    email
            );
        } catch (org.springframework.dao.EmptyResultDataAccessException exception) {
            return Optional.empty();
        }

        return userRepository.findById(id);
    }
}
