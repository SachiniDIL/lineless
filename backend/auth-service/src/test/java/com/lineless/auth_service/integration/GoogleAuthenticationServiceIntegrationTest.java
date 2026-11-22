package com.lineless.auth_service.integration;

import com.lineless.auth_service.entity.IdentityProvider;
import com.lineless.auth_service.entity.User;
import com.lineless.auth_service.entity.UserRole;
import com.lineless.auth_service.entity.UserStatus;
import com.lineless.auth_service.exception.GoogleAccountLinkingException;
import com.lineless.auth_service.repository.UserIdentityRepository;
import com.lineless.auth_service.repository.UserRepository;
import com.lineless.auth_service.service.GoogleAuthenticationService;
import com.lineless.auth_service.service.GoogleTokenService;
import com.lineless.auth_service.service.GoogleUserInfo;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.concurrent.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@SpringBootTest
@Testcontainers
class GoogleAuthenticationServiceIntegrationTest {

    @Container
    static PostgreSQLContainer<?> postgres =
            new PostgreSQLContainer<>("postgres:16-alpine")
                    .withDatabaseName("lineless_test")
                    .withUsername("test")
                    .withPassword("test");
    @Autowired
    private GoogleAuthenticationService googleAuthenticationService;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private UserIdentityRepository userIdentityRepository;
    @MockitoBean
    private GoogleTokenService googleTokenService;

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void shouldAllowOnlyOneGoogleAccountToBeLinkedConcurrently() throws Exception {

        // Arrange: Create an existing LineLess user.
        User user = new User();
        user.setName("Concurrent Google User");
        user.setEmail("concurrent-google@example.com");
        user.setPasswordHash("test-hash");
        user.setRole(UserRole.USER);
        user.setStatus(UserStatus.ACTIVE);

        User savedUser = userRepository.saveAndFlush(user);

        // Mock Google token validation.
        // PostgreSQL and the account-linking logic remain real.
        when(googleTokenService.validateAndExtractUserInfo("google-token-a"))
                .thenReturn(new GoogleUserInfo(
                        "google-sub-a",
                        "concurrent-google@example.com",
                        "Google User A"
                ));

        when(googleTokenService.validateAndExtractUserInfo("google-token-b"))
                .thenReturn(new GoogleUserInfo(
                        "google-sub-b",
                        "concurrent-google@example.com",
                        "Google User B"
                ));

        ExecutorService executor = Executors.newFixedThreadPool(2);

        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);

        try {
            Future<String> requestA = executor.submit(() -> {
                ready.countDown();
                start.await();
                return authenticateOrReject("google-token-a");
            });

            Future<String> requestB = executor.submit(() -> {
                ready.countDown();
                start.await();
                return authenticateOrReject("google-token-b");
            });

            // Wait until both requests are ready.
            assertThat(ready.await(5, TimeUnit.SECONDS)).isTrue();

            // Start both requests together.
            start.countDown();

            String resultA = requestA.get(15, TimeUnit.SECONDS);
            String resultB = requestB.get(15, TimeUnit.SECONDS);

            // Exactly one request should succeed.
            assertThat(resultA.startsWith("SUCCESS"))
                    .isNotEqualTo(resultB.startsWith("SUCCESS"));

            // Exactly one request should be rejected.
            assertThat(resultA.equals("LINK_REJECTED"))
                    .isNotEqualTo(resultB.equals("LINK_REJECTED"));

            // Check what was actually saved in PostgreSQL.
            var identityA = userIdentityRepository
                    .findByProviderAndProviderSubject(
                            IdentityProvider.GOOGLE,
                            "google-sub-a"
                    );

            var identityB = userIdentityRepository
                    .findByProviderAndProviderSubject(
                            IdentityProvider.GOOGLE,
                            "google-sub-b"
                    );

            // Only one Google identity should exist.
            assertThat(identityA.isPresent())
                    .isNotEqualTo(identityB.isPresent());

            // The saved identity must belong to our original user.
            var linkedIdentity = identityA.orElseGet(identityB::orElseThrow);

            assertThat(linkedIdentity.getUser().getId())
                    .isEqualTo(savedUser.getId());

        } finally {
            executor.shutdownNow();
        }
    }


    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void shouldPreventSameGoogleAccountFromBeingLinkedToTwoUsersConcurrently()
            throws Exception {

        // Arrange: Create two different LineLess users.
        User firstUser = new User();
        firstUser.setName("First Google User");
        firstUser.setEmail("first-google@example.com");
        firstUser.setPasswordHash("test-hash");
        firstUser.setRole(UserRole.USER);
        firstUser.setStatus(UserStatus.ACTIVE);

        User savedFirstUser = userRepository.saveAndFlush(firstUser);

        User secondUser = new User();
        secondUser.setName("Second Google User");
        secondUser.setEmail("second-google@example.com");
        secondUser.setPasswordHash("test-hash");
        secondUser.setRole(UserRole.USER);
        secondUser.setStatus(UserStatus.ACTIVE);

        User savedSecondUser = userRepository.saveAndFlush(secondUser);

        // Both requests represent the same verified Google account.
        when(googleTokenService.validateAndExtractUserInfo("shared-google-token-a"))
                .thenReturn(new GoogleUserInfo(
                        "shared-google-subject",
                        "first-google@example.com",
                        "First User"
                ));

        when(googleTokenService.validateAndExtractUserInfo("shared-google-token-b"))
                .thenReturn(new GoogleUserInfo(
                        "shared-google-subject",
                        "second-google@example.com",
                        "Second User"
                ));

        ExecutorService executor = Executors.newFixedThreadPool(2);

        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);

        try {
            Future<String> requestA = executor.submit(() -> {
                ready.countDown();
                start.await();
                return authenticateOrReject("shared-google-token-a");
            });

            Future<String> requestB = executor.submit(() -> {
                ready.countDown();
                start.await();
                return authenticateOrReject("shared-google-token-b");
            });

            assertThat(ready.await(5, TimeUnit.SECONDS)).isTrue();

            // Start both requests together.
            start.countDown();

            String resultA = requestA.get(15, TimeUnit.SECONDS);
            String resultB = requestB.get(15, TimeUnit.SECONDS);

            // Exactly one request should succeed.
            assertThat(resultA.startsWith("SUCCESS"))
                    .isNotEqualTo(resultB.startsWith("SUCCESS"));

            // The other request should be rejected.
            assertThat(resultA.equals("LINK_REJECTED"))
                    .isNotEqualTo(resultB.equals("LINK_REJECTED"));

            // Only one Google identity should exist.
            var identity = userIdentityRepository
                    .findByProviderAndProviderSubject(
                            IdentityProvider.GOOGLE,
                            "shared-google-subject"
                    );

            assertThat(identity).isPresent();

            // It must belong to exactly one of the two users.
            var linkedUserId = identity.orElseThrow().getUser().getId();

            assertThat(linkedUserId)
                    .isIn(savedFirstUser.getId(), savedSecondUser.getId());

        } finally {
            executor.shutdownNow();
        }
    }

    private String authenticateOrReject(String token) {
        try {
            User authenticatedUser =
                    googleAuthenticationService.authenticate(token);

            return "SUCCESS:" + authenticatedUser.getId();

        } catch (GoogleAccountLinkingException exception) {
            return "LINK_REJECTED";
        }
    }
}
