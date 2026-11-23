package com.lineless.auth_service.integration;

import com.lineless.auth_service.entity.IdentityProvider;
import com.lineless.auth_service.entity.User;
import com.lineless.auth_service.entity.UserIdentity;
import com.lineless.auth_service.entity.UserRole;
import com.lineless.auth_service.entity.UserStatus;
import com.lineless.auth_service.repository.UserIdentityRepository;
import com.lineless.auth_service.repository.UserRepository;

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

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@Transactional
@Testcontainers
@SpringBootTest
class UserIdentityRepositoryTest {

    @Autowired
    private UserIdentityRepository userIdentityRepository;

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
    void shouldSaveAndFindGoogleIdentity() {

        // Arrange: Create a LineLess user
        User user = createUser("google@example.com");

        User savedUser = userRepository.save(user);

        // Create a Google identity
        UserIdentity identity = createGoogleIdentity(
                savedUser,
                "google-sub-123"
        );

        // Act: Save and retrieve the identity
        UserIdentity savedIdentity =
                userIdentityRepository.saveAndFlush(identity);

        Optional<UserIdentity> foundIdentity =
                userIdentityRepository.findByProviderAndProviderSubject(
                        IdentityProvider.GOOGLE,
                        "google-sub-123"
                );

        // Assert
        assertThat(foundIdentity).isPresent();

        assertThat(foundIdentity.get().getId())
                .isEqualTo(savedIdentity.getId());

        assertThat(foundIdentity.get().getUser().getId())
                .isEqualTo(savedUser.getId());

        assertThat(foundIdentity.get().getProvider())
                .isEqualTo(IdentityProvider.GOOGLE);
    }

    @Test
    void shouldNotAllowSameGoogleIdentityForDifferentUsers() {

        // Arrange: Create two different LineLess users
        User firstUser = userRepository.save(
                createUser("first@example.com")
        );

        User secondUser = userRepository.save(
                createUser("second@example.com")
        );

        // Link the Google identity to the first user
        UserIdentity firstIdentity = createGoogleIdentity(
                firstUser,
                "google-sub-123"
        );

        userIdentityRepository.saveAndFlush(firstIdentity);

        // Act & Assert: Try linking the same Google identity
        // to a different LineLess user
        UserIdentity duplicateIdentity = createGoogleIdentity(
                secondUser,
                "google-sub-123"
        );

        assertThatThrownBy(() ->
                userIdentityRepository.saveAndFlush(duplicateIdentity)
        ).isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void shouldNotAllowOneUserToLinkMultipleGoogleIdentities() {

        // Arrange: Create a LineLess user
        User user = userRepository.save(
                createUser("multiple-google@example.com")
        );

        // Link the first Google identity
        UserIdentity firstIdentity = createGoogleIdentity(
                user,
                "google-sub-123"
        );

        userIdentityRepository.saveAndFlush(firstIdentity);

        // Act & Assert: Try linking another Google identity
        // to the same LineLess user
        UserIdentity secondIdentity = createGoogleIdentity(
                user,
                "google-sub-456"
        );

        assertThatThrownBy(() ->
                userIdentityRepository.saveAndFlush(secondIdentity)
        ).isInstanceOf(DataIntegrityViolationException.class);
    }

    private User createUser(String email) {

        User user = new User();

        user.setName("Test User");
        user.setEmail(email);
        user.setPasswordHash("test-password-hash");
        user.setRole(UserRole.USER);
        user.setStatus(UserStatus.ACTIVE);

        return user;
    }

    private UserIdentity createGoogleIdentity(
            User user,
            String googleSubject
    ) {

        UserIdentity identity = new UserIdentity();

        identity.setUser(user);
        identity.setProvider(IdentityProvider.GOOGLE);
        identity.setProviderSubject(googleSubject);

        return identity;
    }
}
