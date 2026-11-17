package com.lineless.auth_service.integration;

import com.lineless.auth_service.entity.RefreshToken;
import com.lineless.auth_service.entity.User;
import com.lineless.auth_service.entity.UserRole;
import com.lineless.auth_service.entity.UserStatus;
import com.lineless.auth_service.exception.InvalidRefreshTokenException;
import com.lineless.auth_service.exception.RefreshTokenReuseException;
import com.lineless.auth_service.repository.RefreshTokenRepository;
import com.lineless.auth_service.repository.UserRepository;
import com.lineless.auth_service.service.RefreshTokenService;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.OffsetDateTime;
import java.util.HexFormat;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@Transactional
@Testcontainers
@SpringBootTest
class RefreshTokenIntegrationTest {

    @Autowired
    private RefreshTokenRepository refreshTokenRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private RefreshTokenService refreshTokenService;

    @Autowired
    private EntityManager entityManager;

    @Container
    static PostgreSQLContainer<?> postgres =
            new PostgreSQLContainer<>("postgres:16-alpine")
                    .withDatabaseName("lineless_test")
                    .withUsername("test")
                    .withPassword("test");

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
    }

    @Test
    void shouldSaveAndRetrieveRefreshToken() {
        // Arrange
        User user = new User();
        user.setName("Test User");
        user.setEmail("test@example.com");
        user.setPasswordHash("test-hash");
        user.setRole(UserRole.USER);
        user.setStatus(UserStatus.ACTIVE);

        User savedUser = userRepository.save(user);

        RefreshToken refreshToken = new RefreshToken();
        refreshToken.setUser(savedUser);
        refreshToken.setTokenHash("test-token-hash");
        refreshToken.setFamilyId(UUID.randomUUID());
        refreshToken.setExpiresAt(OffsetDateTime.now().plusDays(7));

        // Act
        RefreshToken savedToken = refreshTokenRepository.save(refreshToken);

        Optional<RefreshToken> retrievedToken =
                refreshTokenRepository.findByTokenHash("test-token-hash");

        // Assert
        assertThat(retrievedToken).isPresent();
        assertThat(retrievedToken.get().getId()).isEqualTo(savedToken.getId());
        assertThat(retrievedToken.get().getUser().getId())
                .isEqualTo(savedUser.getId());
    }

    @Test
    void shouldRotateRefreshTokenSuccessfully() {
        // Arrange: Create a user
        User user = new User();
        user.setName("Rotation Test User");
        user.setEmail("rotation@example.com");
        user.setPasswordHash("test-hash");
        user.setRole(UserRole.USER);
        user.setStatus(UserStatus.ACTIVE);

        User savedUser = userRepository.save(user);

        // Create the original refresh token
        String originalToken = refreshTokenService.createRefreshToken(savedUser);

        // Act: Rotate the original token
        String newToken = refreshTokenService.rotateRefreshToken(originalToken);

        // Retrieve both tokens from PostgreSQL using their hashes
        RefreshToken oldToken = refreshTokenRepository
                .findByTokenHash(hashToken(originalToken))
                .orElseThrow();

        RefreshToken newRefreshToken = refreshTokenRepository
                .findByTokenHash(hashToken(newToken))
                .orElseThrow();

        // Assert
        assertThat(newToken).isNotEqualTo(originalToken);

        assertThat(oldToken.getRevokedAt()).isNotNull();

        assertThat(oldToken.getReplacedBy())
                .isEqualTo(newRefreshToken.getId());

        assertThat(newRefreshToken.getRevokedAt()).isNull();

        assertThat(oldToken.getFamilyId())
                .isEqualTo(newRefreshToken.getFamilyId());
    }

    @Test
    void shouldDetectReusedTokenAndRevokeTokenFamily() {
        // Arrange: Create a user
        User user = new User();
        user.setName("Reuse Test User");
        user.setEmail("reuse@example.com");
        user.setPasswordHash("test-hash");
        user.setRole(UserRole.USER);
        user.setStatus(UserStatus.ACTIVE);

        User savedUser = userRepository.save(user);

        // Create the original refresh token
        String originalToken =
                refreshTokenService.createRefreshToken(savedUser);

        // Rotate it once
        String replacementToken =
                refreshTokenService.rotateRefreshToken(originalToken);

        // Act: Try to reuse the old token
        assertThatThrownBy(() ->
                refreshTokenService.rotateRefreshToken(originalToken)
        ).isInstanceOf(RefreshTokenReuseException.class);

        // Clear Hibernate's first-level cache
        entityManager.clear();

        // Retrieve the replacement token from PostgreSQL
        RefreshToken replacement = refreshTokenRepository
                .findByTokenHash(hashToken(replacementToken))
                .orElseThrow();

        // Assert: The replacement token must also be revoked
        assertThat(replacement.getRevokedAt()).isNotNull();
    }

    @Test
    void shouldRejectExpiredRefreshToken() {
        // Arrange: Create a user
        User user = new User();
        user.setName("Expired Token User");
        user.setEmail("expired@example.com");
        user.setPasswordHash("test-hash");
        user.setRole(UserRole.USER);
        user.setStatus(UserStatus.ACTIVE);

        User savedUser = userRepository.save(user);

        // Create an already-expired refresh token
        RefreshToken expiredToken = new RefreshToken();
        expiredToken.setUser(savedUser);
        expiredToken.setTokenHash(hashToken("expired-test-token"));
        expiredToken.setFamilyId(UUID.randomUUID());
        expiredToken.setExpiresAt(OffsetDateTime.now().minusDays(1));

        refreshTokenRepository.save(expiredToken);

        // Act & Assert: The expired token must be rejected
        assertThatThrownBy(() ->
                refreshTokenService.rotateRefreshToken("expired-test-token")
        ).isInstanceOf(InvalidRefreshTokenException.class);

        // Confirm that no replacement token was created
        assertThat(refreshTokenRepository.findAll())
                .hasSize(1);
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void shouldHandleConcurrentRefreshRequestsSafely() throws Exception {

        // Arrange: Create a user
        User user = new User();
        user.setName("Concurrent Test User");
        user.setEmail("concurrent@example.com");
        user.setPasswordHash("test-hash");
        user.setRole(UserRole.USER);
        user.setStatus(UserStatus.ACTIVE);

        User savedUser = userRepository.save(user);

        // Create the original refresh token
        String originalToken =
                refreshTokenService.createRefreshToken(savedUser);

        // Prepare two threads
        ExecutorService executor = Executors.newFixedThreadPool(2);

        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);

        try {
            // Submit request A
            Future<String> requestA = executor.submit(() -> {
                ready.countDown();
                start.await();

                return rotateOrDetectReuse(originalToken);
            });

            // Submit request B
            Future<String> requestB = executor.submit(() -> {
                ready.countDown();
                start.await();

                return rotateOrDetectReuse(originalToken);
            });

            // Wait until both threads are ready
            assertThat(ready.await(5, TimeUnit.SECONDS)).isTrue();

            // Release both requests at almost the same time
            start.countDown();

            String resultA = requestA.get(15, TimeUnit.SECONDS);
            String resultB = requestB.get(15, TimeUnit.SECONDS);

            // Assert: Exactly one request succeeds
            assertThat(resultA.equals("REUSE_DETECTED"))
                    .isNotEqualTo(resultB.equals("REUSE_DETECTED"));

            // Find the successfully generated replacement token
            String replacementToken = resultA.equals("REUSE_DETECTED")
                    ? resultB
                    : resultA;

            // Clear Hibernate's first-level cache
            entityManager.clear();

            // Retrieve the replacement token from PostgreSQL
            RefreshToken replacement = refreshTokenRepository
                    .findByTokenHash(hashToken(replacementToken))
                    .orElseThrow();

            // The second request should have detected reuse
            // and revoked the entire token family
            assertThat(replacement.getRevokedAt()).isNotNull();

        } finally {
            executor.shutdownNow();
        }
    }

    private String hashToken(String token) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");

            byte[] hashBytes = digest.digest(
                    token.getBytes(StandardCharsets.UTF_8)
            );

            return HexFormat.of().formatHex(hashBytes);

        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException(exception);
        }
    }

    private String rotateOrDetectReuse(String token) {
        try {
            return refreshTokenService.rotateRefreshToken(token);
        } catch (RefreshTokenReuseException exception) {
            return "REUSE_DETECTED";
        }
    }
}