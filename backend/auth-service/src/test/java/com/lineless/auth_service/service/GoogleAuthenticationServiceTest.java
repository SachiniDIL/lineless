package com.lineless.auth_service.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.lineless.auth_service.entity.IdentityProvider;
import com.lineless.auth_service.entity.User;
import com.lineless.auth_service.entity.UserIdentity;
import com.lineless.auth_service.entity.UserRole;
import com.lineless.auth_service.entity.UserStatus;
import com.lineless.auth_service.exception.GoogleAccountLinkingException;
import com.lineless.auth_service.exception.InvalidCredentialsException;
import com.lineless.auth_service.repository.UserIdentityRepository;
import com.lineless.auth_service.repository.UserRepository;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;

@ExtendWith(MockitoExtension.class)
class GoogleAuthenticationServiceTest {

    @Mock
    private GoogleTokenService googleTokenService;

    @Mock
    private UserRepository userRepository;

    @Mock
    private UserIdentityRepository userIdentityRepository;

    private GoogleAuthenticationService googleAuthenticationService;

    private final GoogleUserInfo googleUser = new GoogleUserInfo(
            "google-sub-123",
            "sachini@example.com",
            "Sachini"
    );

    @BeforeEach
    void setUp() {
        googleAuthenticationService = new GoogleAuthenticationService(
                googleTokenService,
                userRepository,
                userIdentityRepository
        );

        when(googleTokenService.validateAndExtractUserInfo("valid-token"))
                .thenReturn(googleUser);
    }

    @Test
    void shouldAuthenticateUserWithExistingGoogleIdentity() {

        User user = createUser(UserStatus.ACTIVE);

        UserIdentity identity = new UserIdentity();
        identity.setUser(user);

        when(userIdentityRepository.findByProviderAndProviderSubject(
                IdentityProvider.GOOGLE,
                "google-sub-123"
        )).thenReturn(Optional.of(identity));

        User result = googleAuthenticationService.authenticate("valid-token");

        assertThat(result).isSameAs(user);
        assertThat(result.isEmailVerified()).isTrue();
        assertThat(result.getEmailVerifiedAt()).isNotNull();

        verify(userRepository, never()).findByEmail(any());
        verify(userIdentityRepository, never()).save(any());
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

        when(userIdentityRepository.existsByUser_IdAndProvider(
                existingUser.getId(),
                IdentityProvider.GOOGLE
        )).thenReturn(false);

        User result = googleAuthenticationService.authenticate("valid-token");

        assertThat(result).isSameAs(existingUser);

        ArgumentCaptor<UserIdentity> captor =
                ArgumentCaptor.forClass(UserIdentity.class);

        verify(userIdentityRepository).save(captor.capture());

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
                    return user;
                });

        User result = googleAuthenticationService.authenticate("valid-token");

        assertThat(result.getName()).isEqualTo("Sachini");
        assertThat(result.getEmail()).isEqualTo("sachini@example.com");
        assertThat(result.getPasswordHash()).isNull();
        assertThat(result.getRole()).isEqualTo(UserRole.USER);
        assertThat(result.getStatus()).isEqualTo(UserStatus.ACTIVE);
        assertThat(result.isEmailVerified()).isTrue();
        assertThat(result.getEmailVerifiedAt()).isNotNull();

        verify(userRepository).save(any(User.class));
        verify(userIdentityRepository).save(any(UserIdentity.class));
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

        verify(userIdentityRepository, never()).save(any());
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

        when(userIdentityRepository.existsByUser_IdAndProvider(
                existingUser.getId(),
                IdentityProvider.GOOGLE
        )).thenReturn(true);

        assertThatThrownBy(() ->
                googleAuthenticationService.authenticate("valid-token"))
                .isInstanceOf(GoogleAccountLinkingException.class)
                .hasMessage("This account is already linked to another Google account");

        verify(userIdentityRepository, never()).save(any());
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
