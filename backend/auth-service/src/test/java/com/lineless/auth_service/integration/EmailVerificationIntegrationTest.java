package com.lineless.auth_service.integration;

import com.lineless.auth_service.entity.EmailVerificationToken;
import com.lineless.auth_service.entity.User;
import com.lineless.auth_service.entity.UserRole;
import com.lineless.auth_service.entity.UserStatus;
import com.lineless.auth_service.exception.InvalidEmailVerificationTokenException;
import com.lineless.auth_service.repository.EmailVerificationTokenRepository;
import com.lineless.auth_service.repository.UserRepository;
import com.lineless.auth_service.service.EmailVerificationService;
import com.lineless.auth_service.service.EmailVerificationTokenService;

import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.annotation.Transactional;

import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@Transactional
@Testcontainers
@SpringBootTest
class EmailVerificationIntegrationTest {

    @Autowired
    private EmailVerificationTokenService tokenService;

    @Autowired
    private EmailVerificationService verificationService;

    @Autowired
    private EmailVerificationTokenRepository tokenRepository;

    @Autowired
    private UserRepository userRepository;

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
    void shouldPersistTokenHashAndVerifyUserEmail() {

        // Arrange: Create an unverified user.
        User user = userRepository.saveAndFlush(
                createUser("verify@example.com")
        );

        // Generate a verification token.
        String rawToken = tokenService.createVerificationToken(user);

        String expectedHash =
                EmailVerificationTokenService.hashTokenValue(rawToken);

        // Confirm that the hash, not the raw token, is stored.
        EmailVerificationToken savedToken = tokenRepository
                .findByTokenHash(expectedHash)
                .orElseThrow();

        assertThat(savedToken.getTokenHash()).isEqualTo(expectedHash);
        assertThat(savedToken.getTokenHash()).isNotEqualTo(rawToken);
        assertThat(savedToken.getUser().getId()).isEqualTo(user.getId());

        // Act: Verify the email.
        verificationService.verifyEmail(rawToken);

        // Assert: Reload the user and token from PostgreSQL.
        User verifiedUser = userRepository.findById(user.getId())
                .orElseThrow();

        EmailVerificationToken usedToken = tokenRepository
                .findByTokenHash(expectedHash)
                .orElseThrow();

        assertThat(verifiedUser.isEmailVerified()).isTrue();
        assertThat(verifiedUser.getEmailVerifiedAt()).isNotNull();

        assertThat(usedToken.getUsedAt()).isNotNull();
    }

    @Test
    void shouldRejectUsingSameTokenTwice() {

        // Arrange
        User user = userRepository.saveAndFlush(
                createUser("reuse@example.com")
        );

        String rawToken = tokenService.createVerificationToken(user);

        // Act: First verification should succeed.
        verificationService.verifyEmail(rawToken);

        // Assert: Second verification should fail.
        assertThatThrownBy(() ->
                verificationService.verifyEmail(rawToken)
        ).isInstanceOf(InvalidEmailVerificationTokenException.class);
    }

    @Test
    void shouldRejectExpiredTokenStoredInDatabase() {

        // Arrange
        User user = userRepository.saveAndFlush(
                createUser("expired@example.com")
        );

        String rawToken = "expired-integration-token";

        EmailVerificationToken token = new EmailVerificationToken();

        token.setUser(user);
        token.setTokenHash(
                EmailVerificationTokenService.hashTokenValue(rawToken)
        );
        token.setExpiresAt(
                OffsetDateTime.now(ZoneOffset.UTC).minusMinutes(5)
        );

        tokenRepository.saveAndFlush(token);

        // Act & Assert
        assertThatThrownBy(() ->
                verificationService.verifyEmail(rawToken)
        ).isInstanceOf(InvalidEmailVerificationTokenException.class);

        User unchangedUser = userRepository.findById(user.getId())
                .orElseThrow();

        assertThat(unchangedUser.isEmailVerified()).isFalse();
    }

    @Test
    void shouldInvalidatePreviousTokenWhenGeneratingNewToken() {

        // Arrange
        User user = userRepository.saveAndFlush(
                createUser("resend@example.com")
        );

        String firstToken = tokenService.createVerificationToken(user);

        String firstHash =
                EmailVerificationTokenService.hashTokenValue(firstToken);

        // Act: Generate a replacement token.
        String secondToken = tokenService.createVerificationToken(user);

        String secondHash =
                EmailVerificationTokenService.hashTokenValue(secondToken);

        // Assert
        EmailVerificationToken oldToken = tokenRepository
                .findByTokenHash(firstHash)
                .orElseThrow();

        EmailVerificationToken newToken = tokenRepository
                .findByTokenHash(secondHash)
                .orElseThrow();

        assertThat(oldToken.getUsedAt()).isNotNull();
        assertThat(newToken.getUsedAt()).isNull();

        assertThat(firstToken).isNotEqualTo(secondToken);

        assertThatThrownBy(() ->
                verificationService.verifyEmail(firstToken)
        ).isInstanceOf(InvalidEmailVerificationTokenException.class);
    }

    @Test
    void shouldNotAllowDuplicateTokenHashes() {

        // Arrange
        User user = userRepository.saveAndFlush(
                createUser("duplicate@example.com")
        );

        String tokenHash =
                EmailVerificationTokenService.hashTokenValue("same-token");

        EmailVerificationToken firstToken = createToken(user, tokenHash);

        tokenRepository.saveAndFlush(firstToken);

        EmailVerificationToken duplicateToken = createToken(user, tokenHash);

        // Act & Assert
        assertThatThrownBy(() ->
                tokenRepository.saveAndFlush(duplicateToken)
        ).isInstanceOf(DataIntegrityViolationException.class);
    }

    private User createUser(String email) {

        User user = new User();

        user.setName("Test User");
        user.setEmail(email);
        user.setPasswordHash("test-password-hash");
        user.setRole(UserRole.USER);
        user.setStatus(UserStatus.ACTIVE);
        user.setEmailVerified(false);

        return user;
    }

    private EmailVerificationToken createToken(
            User user,
            String tokenHash
    ) {

        EmailVerificationToken token = new EmailVerificationToken();

        token.setUser(user);
        token.setTokenHash(tokenHash);
        token.setExpiresAt(
                OffsetDateTime.now(ZoneOffset.UTC).plusMinutes(15)
        );

        return token;
    }
}
