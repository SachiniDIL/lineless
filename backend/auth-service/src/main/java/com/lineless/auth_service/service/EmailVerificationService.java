package com.lineless.auth_service.service;

import com.lineless.auth_service.entity.EmailVerificationToken;
import com.lineless.auth_service.entity.User;
import com.lineless.auth_service.exception.InvalidEmailVerificationTokenException;
import com.lineless.auth_service.repository.EmailVerificationTokenRepository;
import com.lineless.auth_service.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;

@Service
@RequiredArgsConstructor
public class EmailVerificationService {

    private final EmailVerificationTokenRepository tokenRepository;
    private final UserRepository userRepository;

    @Transactional
    public void verifyEmail(String rawToken) {

        if (rawToken == null || rawToken.isBlank()) {
            throw new InvalidEmailVerificationTokenException();
        }

        String tokenHash = EmailVerificationTokenService.hashTokenValue(rawToken);

        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);

        EmailVerificationToken token = tokenRepository
                .findByTokenHashForUpdate(tokenHash)
                .orElseThrow(InvalidEmailVerificationTokenException::new);

        if (token.getUsedAt() != null || !token.getExpiresAt().isAfter(now)) {
            throw new InvalidEmailVerificationTokenException();
        }

        User user = token.getUser();

        if (!user.isEmailVerified()) {
            user.setEmailVerified(true);
            user.setEmailVerifiedAt(now);
            userRepository.save(user);
        }

        token.setUsedAt(now);
        tokenRepository.save(token);
    }
}
