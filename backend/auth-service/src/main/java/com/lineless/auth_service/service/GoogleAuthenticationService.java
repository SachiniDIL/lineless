
package com.lineless.auth_service.service;

import com.lineless.auth_service.entity.*;
import com.lineless.auth_service.exception.GoogleAccountLinkingException;
import com.lineless.auth_service.exception.InvalidCredentialsException;
import com.lineless.auth_service.repository.UserIdentityRepository;
import com.lineless.auth_service.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Locale;

@Service
@RequiredArgsConstructor
public class GoogleAuthenticationService {

    private final GoogleTokenService googleTokenService;
    private final UserRepository userRepository;
    private final UserIdentityRepository userIdentityRepository;

    @Transactional
    public User authenticate(String idToken) {

        GoogleUserInfo googleUser =
                googleTokenService.validateAndExtractUserInfo(idToken);

        // First, check whether this Google identity is already linked.
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

            String googleEmail = normalizeEmail(googleUser.email());
            String accountEmail = normalizeEmail(user.getEmail());

            if (googleEmail.equals(accountEmail)) {
                markEmailAsVerified(user);
            }

            return user;
        }

        // Find the LineLess account using Google's verified email.
        String normalizedEmail = normalizeEmail(googleUser.email());

        User user = userRepository.findByEmail(normalizedEmail)
                .orElseGet(() -> createGoogleUser(googleUser, normalizedEmail));

        // Lock the existing account before checking or creating its identity.
        user = userRepository.findByIdForUpdate(user.getId())
                .orElseThrow(InvalidCredentialsException::new);

        if (user.getStatus() != UserStatus.ACTIVE) {
            throw new InvalidCredentialsException();
        }

        // Recheck the Google subject after acquiring the lock.
        var linkedIdentity = userIdentityRepository
                .findByProviderAndProviderSubject(
                        IdentityProvider.GOOGLE,
                        googleUser.googleSubject()
                );

        if (linkedIdentity.isPresent()) {
            User linkedUser = linkedIdentity.get().getUser();

            if (!linkedUser.getId().equals(user.getId())) {
                throw new GoogleAccountLinkingException(
                        "This Google account is already linked to another account"
                );
            }

            String googleEmail = normalizeEmail(googleUser.email());
            String accountEmail = normalizeEmail(linkedUser.getEmail());

            if (googleEmail.equals(accountEmail)) {
                markEmailAsVerified(linkedUser);
            }

            return linkedUser;
        }

        // Check whether another Google account is already linked.
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

        markEmailAsVerified(user);

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

    private String normalizeEmail(String email) {
        return email.trim().toLowerCase(Locale.ROOT);
    }

    private void markEmailAsVerified(User user) {
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

        try {
            userIdentityRepository.saveAndFlush(identity);
        } catch (DataIntegrityViolationException exception) {
            throw new GoogleAccountLinkingException(
                    "This Google account could not be linked to the account"
            );
        }
    }
}
