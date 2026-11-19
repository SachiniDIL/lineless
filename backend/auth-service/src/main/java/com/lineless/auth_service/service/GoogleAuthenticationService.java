package com.lineless.auth_service.service;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Locale;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.lineless.auth_service.entity.IdentityProvider;
import com.lineless.auth_service.entity.User;
import com.lineless.auth_service.entity.UserIdentity;
import com.lineless.auth_service.entity.UserRole;
import com.lineless.auth_service.entity.UserStatus;
import com.lineless.auth_service.exception.GoogleAccountLinkingException;
import com.lineless.auth_service.exception.InvalidCredentialsException;
import com.lineless.auth_service.repository.UserIdentityRepository;
import com.lineless.auth_service.repository.UserRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class GoogleAuthenticationService {

    private final GoogleTokenService googleTokenService;
    private final UserRepository userRepository;
    private final UserIdentityRepository userIdentityRepository;

    @Transactional
    public User authenticate(String idToken) {

        // Step 1: Verify Google's ID token.
        GoogleUserInfo googleUser =
                googleTokenService.validateAndExtractUserInfo(idToken);

        // Step 2: Look for an already-linked Google identity.
        var existingIdentity = userIdentityRepository
                .findByProviderAndProviderSubject(
                        IdentityProvider.GOOGLE,
                        googleUser.googleSubject()
                );

        if (existingIdentity.isPresent()) {

            User user = existingIdentity.get().getUser();

            if (user.getStatus() != UserStatus.ACTIVE) {
                throw new InvalidCredentialsException();
            }

            markEmailAsVerified(user);

            return user;
        }

        // Step 3: Look for an existing LineLess account by verified email.
        String normalizedEmail = googleUser.email()
                .trim()
                .toLowerCase(Locale.ROOT);

        User user = userRepository.findByEmail(normalizedEmail)
                .orElseGet(() -> createGoogleUser(googleUser, normalizedEmail));

        if (user.getStatus() != UserStatus.ACTIVE) {
            throw new InvalidCredentialsException();
        }

        // Step 4: Mark the Google-verified email as verified.
        markEmailAsVerified(user);

        // Step 5: Prevent replacing an already-linked Google identity.
        boolean alreadyHasGoogleIdentity =
                userIdentityRepository.existsByUser_IdAndProvider(
                        user.getId(),
                        IdentityProvider.GOOGLE
                );

        if (alreadyHasGoogleIdentity) {
            throw new GoogleAccountLinkingException(
                    "This account is already linked to another Google account"
            );
        }

        // Step 6: Link this Google identity to the LineLess user.
        linkGoogleIdentity(user, googleUser.googleSubject());

        return user;
    }

    private User createGoogleUser(
            GoogleUserInfo googleUser,
            String normalizedEmail) {

        User user = new User();

        user.setName(googleUser.name());
        user.setEmail(normalizedEmail);
        user.setPasswordHash(null);
        user.setRole(UserRole.USER);
        user.setStatus(UserStatus.ACTIVE);

        markEmailAsVerified(user);

        return userRepository.save(user);
    }

    private void markEmailAsVerified(User user) {

        // Preserve the original verification timestamp.
        if (!user.isEmailVerified()) {
            user.setEmailVerified(true);
            user.setEmailVerifiedAt(OffsetDateTime.now(ZoneOffset.UTC));
        }
    }

    private void linkGoogleIdentity(User user, String googleSubject) {

        UserIdentity identity = new UserIdentity();

        identity.setUser(user);
        identity.setProvider(IdentityProvider.GOOGLE);
        identity.setProviderSubject(googleSubject);

        userIdentityRepository.save(identity);
    }
}
