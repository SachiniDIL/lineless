package com.lineless.auth_service.service;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.quality.Strictness;
import org.mockito.junit.jupiter.MockitoSettings;

import com.lineless.auth_service.entity.RefreshToken;
import com.lineless.auth_service.entity.User;
import com.lineless.auth_service.entity.UserStatus;
import com.lineless.auth_service.exception.InvalidRefreshTokenException;
import com.lineless.auth_service.exception.RefreshTokenReuseException;
import com.lineless.auth_service.repository.RefreshTokenRepository;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class RefreshTokenServiceTest {

    private static final long REFRESH_TOKEN_EXPIRATION_SECONDS = 604_800;

    @Mock
    private RefreshTokenRepository refreshTokenRepository;

    private RefreshTokenService refreshTokenService;

    @BeforeEach
    void setUp() {
        refreshTokenService = new RefreshTokenService(
                refreshTokenRepository,
                REFRESH_TOKEN_EXPIRATION_SECONDS);
        when(refreshTokenRepository.save(any(RefreshToken.class)))
                .thenAnswer(invocation -> {
                    RefreshToken token = invocation.getArgument(0);
                    if (token.getId() == null) {
                        token.setId(UUID.randomUUID());
                    }
                    return token;
                });
    }

    @Test
    void generatesRandomRefreshTokens() {
        User user = new User();

        String firstToken = refreshTokenService.createRefreshToken(user);
        String secondToken = refreshTokenService.createRefreshToken(user);

        assertThat(firstToken)
                .isNotBlank()
                .isNotEqualTo(secondToken);
    }

    @Test
    void savesOnlyTheHashedToken() throws Exception {
        User user = new User();
        String rawToken = refreshTokenService.createRefreshToken(user);

        ArgumentCaptor<RefreshToken> captor = ArgumentCaptor.forClass(RefreshToken.class);
        verify(refreshTokenRepository).save(captor.capture());

        String savedHash = captor.getValue().getTokenHash();
        assertThat(savedHash)
                .isNotEqualTo(rawToken)
                .isEqualTo(sha256(rawToken));
    }

    @Test
    void setsCorrectTokenExpirationTime() {
        User user = new User();
        OffsetDateTime beforeCreation = OffsetDateTime.now();

        refreshTokenService.createRefreshToken(user);

        OffsetDateTime expiresAt = capturedToken().getExpiresAt();
        OffsetDateTime expectedLowerBound = beforeCreation
                .plusSeconds(REFRESH_TOKEN_EXPIRATION_SECONDS);
        OffsetDateTime expectedUpperBound = OffsetDateTime.now()
                .plusSeconds(REFRESH_TOKEN_EXPIRATION_SECONDS);

        assertThat(expiresAt).isBetween(expectedLowerBound, expectedUpperBound);
    }

    @Test
    void rotatesTokenSuccessfully() {
        User user = activeUser();
        UUID familyId = UUID.randomUUID();
        UUID oldTokenId = UUID.randomUUID();
        RefreshToken oldToken = refreshToken(
                oldTokenId, user, "old-token", familyId,
                OffsetDateTime.now().plusMinutes(10));
        when(refreshTokenRepository.findByTokenHashForUpdate(sha256Unchecked("old-token")))
                .thenReturn(Optional.of(oldToken));

        String newRawToken = refreshTokenService.rotateRefreshToken("old-token");

        assertThat(newRawToken).isNotBlank().isNotEqualTo("old-token");
        assertThat(oldToken.getRevokedAt()).isNotNull();
        assertThat(oldToken.getReplacedBy()).isNotNull();

        ArgumentCaptor<RefreshToken> captor = ArgumentCaptor.forClass(RefreshToken.class);
        verify(refreshTokenRepository, times(2)).save(captor.capture());
        RefreshToken newToken = captor.getAllValues().get(0);
        assertThat(newToken.getFamilyId()).isEqualTo(familyId);
        assertThat(newToken.getTokenHash()).isEqualTo(sha256Unchecked(newRawToken));
        assertThat(oldToken.getReplacedBy()).isEqualTo(newToken.getId());
    }

    @Test
    void rejectsRefreshTokenReuse() {
        User user = activeUser();
        UUID familyId = UUID.randomUUID();
        RefreshToken revokedToken = refreshToken(
                UUID.randomUUID(), user, "reused-token", familyId,
                OffsetDateTime.now().plusMinutes(10));
        revokedToken.setRevokedAt(OffsetDateTime.now().minusMinutes(1));
        when(refreshTokenRepository.findByTokenHashForUpdate(sha256Unchecked("reused-token")))
                .thenReturn(Optional.of(revokedToken));

        assertThatThrownBy(() -> refreshTokenService.rotateRefreshToken("reused-token"))
                .isInstanceOf(RefreshTokenReuseException.class);
        verify(refreshTokenRepository).revokeActiveTokensByFamilyId(
                org.mockito.ArgumentMatchers.eq(revokedToken.getFamilyId()),
                any(OffsetDateTime.class)
        );
    }

    @Test
    void rejectsExpiredRefreshToken() {
        User user = activeUser();
        RefreshToken expiredToken = refreshToken(
                UUID.randomUUID(), user, "expired-token", UUID.randomUUID(),
                OffsetDateTime.now().minusSeconds(1));
        when(refreshTokenRepository.findByTokenHashForUpdate(sha256Unchecked("expired-token")))
                .thenReturn(Optional.of(expiredToken));

        assertThatThrownBy(() -> refreshTokenService.rotateRefreshToken("expired-token"))
                .isInstanceOf(InvalidRefreshTokenException.class);
    }


    @Test
    void revokesActiveRefreshToken() {
        User user = activeUser();
        RefreshToken token = refreshToken(
                UUID.randomUUID(),
                user,
                "active-token",
                UUID.randomUUID(),
                OffsetDateTime.now().plusMinutes(10)
        );

        when(refreshTokenRepository.findByTokenHashForUpdate(
                sha256Unchecked("active-token")
        )).thenReturn(Optional.of(token));

        refreshTokenService.revokeRefreshToken("active-token");

        assertThat(token.getRevokedAt()).isNotNull();

        verify(refreshTokenRepository).save(token);
    }

    @Test
    void doesNotThrowWhenRefreshTokenDoesNotExist() {
        when(refreshTokenRepository.findByTokenHashForUpdate(
                sha256Unchecked("unknown-token")
        )).thenReturn(Optional.empty());

        assertThatCode(() ->
                refreshTokenService.revokeRefreshToken("unknown-token")
        ).doesNotThrowAnyException();

        verify(refreshTokenRepository, never()).save(any(RefreshToken.class));
    }

    @Test
    void doesNotChangeAlreadyRevokedRefreshToken() {
        User user = activeUser();

        OffsetDateTime originalRevocationTime =
                OffsetDateTime.now().minusMinutes(5);

        RefreshToken token = refreshToken(
                UUID.randomUUID(),
                user,
                "revoked-token",
                UUID.randomUUID(),
                OffsetDateTime.now().plusMinutes(10)
        );

        token.setRevokedAt(originalRevocationTime);

        when(refreshTokenRepository.findByTokenHashForUpdate(
                sha256Unchecked("revoked-token")
        )).thenReturn(Optional.of(token));

        refreshTokenService.revokeRefreshToken("revoked-token");

        assertThat(token.getRevokedAt()).isEqualTo(originalRevocationTime);

        verify(refreshTokenRepository, never()).save(any(RefreshToken.class));
    }

    private User activeUser() {
        User user = new User();
        user.setStatus(UserStatus.ACTIVE);
        return user;
    }

    private RefreshToken refreshToken(
            UUID id,
            User user,
            String rawToken,
            UUID familyId,
            OffsetDateTime expiresAt) {
        RefreshToken token = new RefreshToken();
        token.setId(id);
        token.setUser(user);
        token.setTokenHash(sha256Unchecked(rawToken));
        token.setFamilyId(familyId);
        token.setExpiresAt(expiresAt);
        return token;
    }

    private RefreshToken capturedToken() {
        ArgumentCaptor<RefreshToken> captor = ArgumentCaptor.forClass(RefreshToken.class);
        verify(refreshTokenRepository).save(captor.capture());
        return captor.getValue();
    }

    private String sha256(String token) throws Exception {
        byte[] hash = MessageDigest.getInstance("SHA-256")
                .digest(token.getBytes(StandardCharsets.UTF_8));
        return java.util.HexFormat.of().formatHex(hash);
    }

    private String sha256Unchecked(String token) {
        try {
            return sha256(token);
        } catch (Exception exception) {
            throw new AssertionError(exception);
        }
    }
}
