package com.lineless.auth_service.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.interfaces.RSAPublicKey;
import java.time.Instant;
import java.util.Date;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtException;

import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;

class JwtConfigTest {

    private static final String ISSUER = "https://lineless-auth";
    private static final String AUDIENCE = "lineless-api";

    private KeyPair keyPair;
    private JwtDecoder decoder;

    @BeforeEach
    void setUp() throws Exception {

        KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(2048);

        keyPair = generator.generateKeyPair();

        decoder = new JwtConfig().jwtDecoder(
                (RSAPublicKey) keyPair.getPublic(),
                ISSUER,
                AUDIENCE
        );
    }

    @Test
    void shouldAcceptValidAccessToken() throws Exception {

        String token = createToken(
                ISSUER,
                AUDIENCE,
                Instant.now().plusSeconds(300)
        );

        Jwt decodedToken = decoder.decode(token);

        assertThat(decodedToken.getSubject())
                .isEqualTo("test-user-123");

        assertThat(decodedToken.getIssuer().toString())
                .isEqualTo(ISSUER);

        assertThat(decodedToken.getAudience())
                .contains(AUDIENCE);
    }

    @Test
    void shouldRejectTokenWithWrongIssuer() throws Exception {

        String token = createToken(
                "https://untrusted-issuer",
                AUDIENCE,
                Instant.now().plusSeconds(300)
        );

        assertThatThrownBy(() -> decoder.decode(token))
                .isInstanceOf(JwtException.class);
    }

    @Test
    void shouldRejectTokenWithWrongAudience() throws Exception {

        String token = createToken(
                ISSUER,
                "another-api",
                Instant.now().plusSeconds(300)
        );

        assertThatThrownBy(() -> decoder.decode(token))
                .isInstanceOf(JwtException.class);
    }

    @Test
    void shouldRejectExpiredAccessToken() throws Exception {

        String token = createToken(
                ISSUER,
                AUDIENCE,
                Instant.now().minusSeconds(60)
        );

        assertThatThrownBy(() -> decoder.decode(token))
                .isInstanceOf(JwtException.class);
    }

    @Test
    void shouldRejectTokenWithInvalidSignature() throws Exception {

        KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(2048);

        KeyPair differentKeyPair = generator.generateKeyPair();

        String token = createToken(
                differentKeyPair,
                ISSUER,
                AUDIENCE,
                Instant.now().plusSeconds(300)
        );

        assertThatThrownBy(() -> decoder.decode(token))
                .isInstanceOf(JwtException.class);
    }

    private String createToken(
            String issuer,
            String audience,
            Instant expiration
    ) throws Exception {

        return createToken(keyPair, issuer, audience, expiration);
    }

    private String createToken(
            KeyPair signingKeyPair,
            String issuer,
            String audience,
            Instant expiration
    ) throws Exception {

        JWTClaimsSet claims = new JWTClaimsSet.Builder()
                .issuer(issuer)
                .subject("test-user-123")
                .audience(List.of(audience))
                .issueTime(Date.from(Instant.now()))
                .expirationTime(Date.from(expiration))
                .build();

        SignedJWT signedJWT = new SignedJWT(
                new JWSHeader.Builder(JWSAlgorithm.RS256).build(),
                claims
        );

        signedJWT.sign(
                new RSASSASigner(
                        (java.security.interfaces.RSAPrivateKey)
                                signingKeyPair.getPrivate()
                )
        );

        return signedJWT.serialize();
    }
}
