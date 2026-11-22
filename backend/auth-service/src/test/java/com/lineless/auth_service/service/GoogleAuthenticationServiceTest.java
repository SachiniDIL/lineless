package com.lineless.auth_service.service;

import com.lineless.auth_service.entity.*;
import com.lineless.auth_service.exception.GoogleAccountLinkingException;
import com.lineless.auth_service.exception.InvalidCredentialsException;
import com.lineless.auth_service.exception.InvalidGoogleTokenException;
import com.lineless.auth_service.repository.UserIdentityRepository;
import com.lineless.auth_service.repository.UserRepository;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class GoogleAuthenticationServiceTest {

    private final GoogleUserInfo googleUser = new GoogleUserInfo(
            "google-sub-123",
            "sachini@example.com",
            "Sachini"
    );

    @Mock
    private GoogleTokenService googleTokenService;

    @Mock
    private UserRepository userRepository;

    @Mock
    private UserIdentityRepository userIdentityRepository;

    private GoogleAuthenticationService googleAuthenticationService;

    @BeforeEach
    void setUp() {
        googleAuthenticationService = new GoogleAuthenticationService(
                googleTokenService,
                userRepository,
                userIdentityRepository
        );

        lenient().when(googleTokenService.validateAndExtractUserInfo("valid-token"))
                .thenReturn(googleUser);
    }

    @Test
    void shouldAuthenticateUserWithExistingGoogleIdentity() {

        User user = createUser(UserStatus.ACTIVE);

        GoogleUserInfo googleUserInfo = new GoogleUserInfo(
                "google-sub-123",
                "different@example.com",
                "Sachini"
        );

        UserIdentity identity = new UserIdentity();
        identity.setUser(user);

        when(googleTokenService.validateAndExtractUserInfo("valid-token"))
                .thenReturn(googleUserInfo);

        when(userIdentityRepository.findByProviderAndProviderSubject(
                IdentityProvider.GOOGLE,
                "google-sub-123"
        )).thenReturn(Optional.of(identity));

        User result = googleAuthenticationService.authenticate("valid-token");

        assertThat(result).isSameAs(user);
        assertThat(result.isEmailVerified()).isFalse();
        assertThat(result.getEmailVerifiedAt()).isNull();

        verify(userRepository, never()).findByEmail(anyString());
        verify(userIdentityRepository, never()).saveAndFlush(any());
    }

    @Test
    void shouldLinkGoogleIdentityToExistingEmailAccount() {

        User existingUser = createUser(UserStatus.ACTIVE);

        existingUser.setEmailVerified(false);
        existingUser.setEmailVerifiedAt(null);

        when(userIdentityRepository.findByProviderAndProviderSubject(
                IdentityProvider.GOOGLE,
                "google-sub-123"
        )).thenReturn(Optional.empty());

        when(userRepository.findByEmail("sachini@example.com"))
                .thenReturn(Optional.of(existingUser));

        // Return the existing user after acquiring the database lock.
        when(userRepository.findByIdForUpdate(existingUser.getId()))
                .thenReturn(Optional.of(existingUser));

        when(userIdentityRepository.existsByUser_IdAndProvider(
                existingUser.getId(),
                IdentityProvider.GOOGLE
        )).thenReturn(false);

        User result = googleAuthenticationService.authenticate("valid-token");

        assertThat(result).isSameAs(existingUser);

        ArgumentCaptor<UserIdentity> captor =
                ArgumentCaptor.forClass(UserIdentity.class);

        verify(userIdentityRepository).saveAndFlush(captor.capture());

        UserIdentity savedIdentity = captor.getValue();

        assertThat(savedIdentity.getUser()).isSameAs(existingUser);
        assertThat(savedIdentity.getProvider()).isEqualTo(IdentityProvider.GOOGLE);
        assertThat(savedIdentity.getProviderSubject()).isEqualTo("google-sub-123");
        assertThat(result.isEmailVerified()).isTrue();
        assertThat(result.getEmailVerifiedAt()).isNotNull();

        verify(userRepository, never()).save(any());
    }

    @Test
    void shouldCreateNewUserAndLinkGoogleIdentity() {

        AtomicReference<User> createdUser = new AtomicReference<>();

        when(userIdentityRepository.findByProviderAndProviderSubject(
                IdentityProvider.GOOGLE,
                "google-sub-123"
        )).thenReturn(Optional.empty());

        when(userRepository.findByEmail("sachini@example.com"))
                .thenReturn(Optional.empty());

        when(userRepository.save(any(User.class)))
                .thenAnswer(invocation -> {
                    User user = invocation.getArgument(0);
                    user.setId(UUID.randomUUID());
                    createdUser.set(user);
                    return user;
                });

        // Return the newly created user after acquiring the database lock.
        when(userRepository.findByIdForUpdate(any(UUID.class)))
                .thenAnswer(invocation -> {
                    UUID requestedId = invocation.getArgument(0);
                    User user = createdUser.get();

                    if (user != null && user.getId().equals(requestedId)) {
                        return Optional.of(user);
                    }

                    return Optional.empty();
                });

        when(userIdentityRepository.existsByUser_IdAndProvider(
                any(UUID.class),
                eq(IdentityProvider.GOOGLE)
        )).thenReturn(false);

        User result = googleAuthenticationService.authenticate("valid-token");

        assertThat(result.getName()).isEqualTo("Sachini");
        assertThat(result.getEmail()).isEqualTo("sachini@example.com");
        assertThat(result.getPasswordHash()).isNull();
        assertThat(result.getRole()).isEqualTo(UserRole.USER);
        assertThat(result.getStatus()).isEqualTo(UserStatus.ACTIVE);
        assertThat(result.isEmailVerified()).isTrue();
        assertThat(result.getEmailVerifiedAt()).isNotNull();

        verify(userRepository).save(any(User.class));
        verify(userRepository).findByIdForUpdate(result.getId());
        verify(userIdentityRepository).saveAndFlush(any(UserIdentity.class));
    }

    @Test
    void shouldRejectInactiveUserWithExistingGoogleIdentity() {

        User inactiveUser = createUser(UserStatus.INACTIVE);

        UserIdentity identity = new UserIdentity();
        identity.setUser(inactiveUser);

        when(userIdentityRepository.findByProviderAndProviderSubject(
                IdentityProvider.GOOGLE,
                "google-sub-123"
        )).thenReturn(Optional.of(identity));

        assertThatThrownBy(() ->
                googleAuthenticationService.authenticate("valid-token"))
                .isInstanceOf(InvalidCredentialsException.class);

        verify(userIdentityRepository, never()).saveAndFlush(any());
    }

    @Test
    void shouldRejectLinkingAnotherGoogleAccountToSameUser() {

        User existingUser = createUser(UserStatus.ACTIVE);

        when(userIdentityRepository.findByProviderAndProviderSubject(
                IdentityProvider.GOOGLE,
                "google-sub-123"
        )).thenReturn(Optional.empty());

        when(userRepository.findByEmail("sachini@example.com"))
                .thenReturn(Optional.of(existingUser));

        // Return the existing user after acquiring the database lock.
        when(userRepository.findByIdForUpdate(existingUser.getId()))
                .thenReturn(Optional.of(existingUser));

        when(userIdentityRepository.existsByUser_IdAndProvider(
                existingUser.getId(),
                IdentityProvider.GOOGLE
        )).thenReturn(true);

        assertThatThrownBy(() ->
                googleAuthenticationService.authenticate("valid-token"))
                .isInstanceOf(GoogleAccountLinkingException.class)
                .hasMessage("This account is already linked to another Google account");

        verify(userIdentityRepository, never()).saveAndFlush(any());
    }

    @Test
    void shouldPreserveExistingEmailVerificationTimestamp() {

        User user = createUser(UserStatus.ACTIVE);

        OffsetDateTime originalVerificationTime =
                OffsetDateTime.of(
                        2026, 10, 10,
                        10, 30, 0, 0,
                        ZoneOffset.UTC
                );

        user.setEmailVerified(true);
        user.setEmailVerifiedAt(originalVerificationTime);

        UserIdentity identity = new UserIdentity();
        identity.setUser(user);

        when(userIdentityRepository.findByProviderAndProviderSubject(
                IdentityProvider.GOOGLE,
                "google-sub-123"
        )).thenReturn(Optional.of(identity));

        User result = googleAuthenticationService.authenticate("valid-token");

        assertThat(result.isEmailVerified()).isTrue();
        assertThat(result.getEmailVerifiedAt())
                .isEqualTo(originalVerificationTime);
    }

    @Test
    void shouldRejectLinkingGoogleAccountToInactiveEmailAccount() {

        User inactiveUser = createUser(UserStatus.INACTIVE);

        when(userIdentityRepository.findByProviderAndProviderSubject(
                IdentityProvider.GOOGLE,
                "google-sub-123"
        )).thenReturn(Optional.empty());

        when(userRepository.findByEmail("sachini@example.com"))
                .thenReturn(Optional.of(inactiveUser));

        // Ensure the test reaches the actual inactive-account check.
        when(userRepository.findByIdForUpdate(inactiveUser.getId()))
                .thenReturn(Optional.of(inactiveUser));

        assertThatThrownBy(() ->
                googleAuthenticationService.authenticate("valid-token")
        ).isInstanceOf(InvalidCredentialsException.class);

        verify(userIdentityRepository, never()).saveAndFlush(any());
    }

    @Test
    void shouldRejectInvalidGoogleTokenBeforeAnyDatabaseLookup() {

        when(googleTokenService.validateAndExtractUserInfo("invalid-token"))
                .thenThrow(new InvalidGoogleTokenException("Invalid Google ID token"));

        assertThatThrownBy(() ->
                googleAuthenticationService.authenticate("invalid-token")
        ).isInstanceOf(InvalidGoogleTokenException.class);

        verifyNoInteractions(userRepository, userIdentityRepository);
    }

    @Test
    void shouldNotVerifyAccountEmailWhenGoogleEmailDoesNotMatch() {

        User user = createUser(UserStatus.ACTIVE);
        user.setEmail("old@example.com");
        user.setEmailVerified(false);
        user.setEmailVerifiedAt(null);

        UserIdentity identity = new UserIdentity();
        identity.setUser(user);
        identity.setProvider(IdentityProvider.GOOGLE);
        identity.setProviderSubject("google-sub-123");

        when(googleTokenService.validateAndExtractUserInfo("valid-token"))
                .thenReturn(new GoogleUserInfo(
                        "google-sub-123",
                        "new@example.com",
                        "Sachini"
                ));

        when(userIdentityRepository.findByProviderAndProviderSubject(
                IdentityProvider.GOOGLE,
                "google-sub-123"
        )).thenReturn(Optional.of(identity));

        User result = googleAuthenticationService.authenticate("valid-token");

        assertThat(result).isSameAs(user);
        assertThat(user.isEmailVerified()).isFalse();
        assertThat(user.getEmailVerifiedAt()).isNull();

        verifyNoInteractions(userRepository);
        verify(userIdentityRepository, never()).saveAndFlush(any());
    }

    private User createUser(UserStatus status) {

        User user = new User();

        user.setId(UUID.randomUUID());
        user.setName("Sachini");
        user.setEmail("sachini@example.com");
        user.setPasswordHash("hashed-password");
        user.setRole(UserRole.USER);
        user.setStatus(status);

        return user;
    }
}
