package com.lineless.auth_service.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.time.Instant;
import java.util.Date;

import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import com.sun.net.httpserver.HttpServer;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtException;

class GoogleTokenConfigTest {

    private static final String CLIENT_ID = "test-client-id";
    private static final String ISSUER = "https://accounts.google.com";

    private KeyPair googleKeyPair;
    private HttpServer jwkServer;
    private JwtDecoder decoder;

    @BeforeEach
    void setUp() throws Exception {

        KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(2048);

        googleKeyPair = generator.generateKeyPair();

        RSAKey publicJwk = new RSAKey.Builder(
                (java.security.interfaces.RSAPublicKey) googleKeyPair.getPublic()
        ).keyID("test-key").build();

        String jwkJson = new JWKSet(publicJwk.toPublicJWK()).toString();
        byte[] response = jwkJson.getBytes(StandardCharsets.UTF_8);

        jwkServer = HttpServer.create(
                new InetSocketAddress("localhost", 0), 0
        );

        jwkServer.createContext("/jwks", exchange -> {
            exchange.getResponseHeaders().set(
                    "Content-Type", "application/json"
            );

            exchange.sendResponseHeaders(200, response.length);

            try (var output = exchange.getResponseBody()) {
                output.write(response);
            }
        });

        jwkServer.start();

        String jwkSetUri = "http://localhost:"
                + jwkServer.getAddress().getPort()
                + "/jwks";

        decoder = new GoogleTokenConfig().googleJwtDecoder(
                CLIENT_ID,
                ISSUER,
                jwkSetUri
        );
    }

    @AfterEach
    void tearDown() {
        if (jwkServer != null) {
            jwkServer.stop(0);
        }
    }

    @Test
    void shouldAcceptValidGoogleIdToken() throws Exception {

        String token = createToken(
                googleKeyPair,
                CLIENT_ID,
                Instant.now().plusSeconds(300)
        );

        Jwt decodedToken = decoder.decode(token);

        assertThat(decodedToken.getSubject()).isEqualTo("google-user-123");
        assertThat(decodedToken.getIssuer().toString()).isEqualTo(ISSUER);
        assertThat(decodedToken.getAudience()).contains(CLIENT_ID);
    }

    @Test
    void shouldRejectTokenWithWrongAudience() throws Exception {

        String token = createToken(
                googleKeyPair,
                "wrong-client-id",
                Instant.now().plusSeconds(300)
        );

        assertThatThrownBy(() -> decoder.decode(token))
                .isInstanceOf(JwtException.class);
    }

    @Test
    void shouldRejectExpiredToken() throws Exception {

        String token = createToken(
                googleKeyPair,
                CLIENT_ID,
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
                CLIENT_ID,
                Instant.now().plusSeconds(300)
        );

        assertThatThrownBy(() -> decoder.decode(token))
                .isInstanceOf(JwtException.class);
    }

    private String createToken(
            KeyPair signingKeyPair,
            String audience,
            Instant expiration
    ) throws Exception {

        JWTClaimsSet claims = new JWTClaimsSet.Builder()
                .issuer(ISSUER)
                .subject("google-user-123")
                .audience(audience)
                .issueTime(Date.from(Instant.now()))
                .expirationTime(Date.from(expiration))
                .build();

        SignedJWT signedJWT = new SignedJWT(
                new JWSHeader.Builder(JWSAlgorithm.RS256)
                        .keyID("test-key")
                        .build(),
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
