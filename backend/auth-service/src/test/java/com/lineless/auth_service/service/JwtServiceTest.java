
package com.lineless.auth_service.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.time.Instant;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;

import com.lineless.auth_service.entity.User;
import com.lineless.auth_service.entity.UserRole;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.source.ImmutableJWKSet;
import com.nimbusds.jose.jwk.source.JWKSource;
import com.nimbusds.jose.proc.SecurityContext;

class JwtServiceTest {

    private JwtService jwtService;
    private JwtDecoder jwtDecoder;

    private final String issuer = "https://lineless-auth";
    private final String audience = "lineless-api";
    private final long expiration = 900;

    @BeforeEach
    void setUp() throws Exception {

        KeyPairGenerator keyPairGenerator =
                KeyPairGenerator.getInstance("RSA");

        keyPairGenerator.initialize(2048);

        KeyPair keyPair = keyPairGenerator.generateKeyPair();

        RSAPublicKey publicKey =
                (RSAPublicKey) keyPair.getPublic();

        RSAPrivateKey privateKey =
                (RSAPrivateKey) keyPair.getPrivate();

        RSAKey rsaKey = new RSAKey.Builder(publicKey)
                .privateKey(privateKey)
                .build();

        JWKSource<SecurityContext> jwkSource =
                new ImmutableJWKSet<>(new JWKSet(rsaKey));

        JwtEncoder jwtEncoder = new NimbusJwtEncoder(jwkSource);

        jwtDecoder = NimbusJwtDecoder.withPublicKey(publicKey).build();

        jwtService = new JwtService(
                jwtEncoder,
                issuer,
                audience,
                expiration
        );
    }

    @Test
    void shouldGenerateValidAccessToken() {

        User user = createUser(UserRole.USER);

        String token = jwtService.generateAccessToken(user);

        assertThat(token).isNotBlank();

        Jwt decodedToken = jwtDecoder.decode(token);

        assertThat(decodedToken.getSubject())
                .isEqualTo(user.getId().toString());
    }

    @Test
    void shouldIncludeCorrectUserRole() {

        User user = createUser(UserRole.ADMIN);

        String token = jwtService.generateAccessToken(user);

        Jwt decodedToken = jwtDecoder.decode(token);

        assertThat(decodedToken.getClaimAsStringList("roles"))
                .containsExactly("ADMIN");
    }

    @Test
    void shouldIncludeCorrectIssuerAndAudience() {

        User user = createUser(UserRole.USER);

        String token = jwtService.generateAccessToken(user);

        Jwt decodedToken = jwtDecoder.decode(token);

        assertThat(decodedToken.getIssuer().toString())
                .isEqualTo(issuer);

        assertThat(decodedToken.getAudience())
                .containsExactly(audience);
    }


    @Test
    void shouldSetCorrectExpirationTime() {

        User user = createUser(UserRole.USER);

        Instant beforeGeneration = Instant.now();

        String token = jwtService.generateAccessToken(user);

        Instant afterGeneration = Instant.now();

        Jwt decodedToken = jwtDecoder.decode(token);

        long expectedMinimum = beforeGeneration
                .plusSeconds(expiration)
                .getEpochSecond();

        long expectedMaximum = afterGeneration
                .plusSeconds(expiration)
                .getEpochSecond();

        long actualExpiration = decodedToken
                .getExpiresAt()
                .getEpochSecond();

        assertThat(actualExpiration)
                .isBetween(expectedMinimum, expectedMaximum);
    }


    private User createUser(UserRole role) {

        User user = new User();

        user.setId(UUID.randomUUID());
        user.setName("Test User");
        user.setEmail("test@example.com");
        user.setRole(role);

        return user;
    }
}
