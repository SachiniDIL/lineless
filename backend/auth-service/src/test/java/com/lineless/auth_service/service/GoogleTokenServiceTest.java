package com.lineless.auth_service.service;

import com.lineless.auth_service.exception.InvalidGoogleTokenException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class GoogleTokenServiceTest {

    private JwtDecoder googleJwtDecoder;
    private GoogleTokenService googleTokenService;

    @BeforeEach
    void setUp() {
        googleJwtDecoder = mock(JwtDecoder.class);

        googleTokenService = new GoogleTokenService(
                googleJwtDecoder
        );
    }

    @Test
    void shouldExtractUserInformationFromVerifiedGoogleToken() {

        // Arrange
        Jwt jwt = createGoogleJwt(
                true,
                "google-sub-123",
                "sachini@example.com",
                "Sachini"
        );

        when(googleJwtDecoder.decode("valid-google-token"))
                .thenReturn(jwt);

        // Act
        GoogleUserInfo userInfo =
                googleTokenService.validateAndExtractUserInfo(
                        "valid-google-token"
                );

        // Assert
        assertThat(userInfo.googleSubject())
                .isEqualTo("google-sub-123");

        assertThat(userInfo.email())
                .isEqualTo("sachini@example.com");

        assertThat(userInfo.name())
                .isEqualTo("Sachini");
    }

    @Test
    void shouldNormalizeEmailToLowercase() {

        // Arrange
        Jwt jwt = createGoogleJwt(
                true,
                "google-sub-123",
                "SACHINI@EXAMPLE.COM",
                "Sachini"
        );

        when(googleJwtDecoder.decode("valid-google-token"))
                .thenReturn(jwt);

        // Act
        GoogleUserInfo userInfo =
                googleTokenService.validateAndExtractUserInfo(
                        "valid-google-token"
                );

        // Assert
        assertThat(userInfo.email())
                .isEqualTo("sachini@example.com");
    }

    @Test
    void shouldRejectUnverifiedGoogleEmail() {

        // Arrange
        Jwt jwt = createGoogleJwt(
                false,
                "google-sub-123",
                "sachini@example.com",
                "Sachini"
        );

        when(googleJwtDecoder.decode("unverified-token"))
                .thenReturn(jwt);

        // Act & Assert
        assertThatThrownBy(() ->
                googleTokenService.validateAndExtractUserInfo(
                        "unverified-token"
                )
        ).isInstanceOf(InvalidGoogleTokenException.class)
                .hasMessage("Google email is not verified");
    }

    @Test
    void shouldRejectTokenWithMissingEmail() {

        // Arrange
        Jwt jwt = createGoogleJwt(
                true,
                "google-sub-123",
                null,
                "Sachini"
        );

        when(googleJwtDecoder.decode("missing-email-token"))
                .thenReturn(jwt);

        // Act & Assert
        assertThatThrownBy(() ->
                googleTokenService.validateAndExtractUserInfo(
                        "missing-email-token"
                )
        ).isInstanceOf(InvalidGoogleTokenException.class);
    }

    @Test
    void shouldRejectTokenWithMissingName() {

        // Arrange
        Jwt jwt = createGoogleJwt(
                true,
                "google-sub-123",
                "sachini@example.com",
                null
        );

        when(googleJwtDecoder.decode("missing-name-token"))
                .thenReturn(jwt);

        // Act & Assert
        assertThatThrownBy(() ->
                googleTokenService.validateAndExtractUserInfo(
                        "missing-name-token"
                )
        ).isInstanceOf(InvalidGoogleTokenException.class);
    }

    @Test
    void shouldRejectInvalidGoogleToken() {

        // Arrange
        when(googleJwtDecoder.decode("invalid-token"))
                .thenThrow(new JwtException("Invalid token"));

        // Act & Assert
        assertThatThrownBy(() ->
                googleTokenService.validateAndExtractUserInfo(
                        "invalid-token"
                )
        ).isInstanceOf(InvalidGoogleTokenException.class)
                .hasMessage("Invalid Google ID token");
    }

    @Test
    void shouldRejectTokenWithMissingSubject() {

        Jwt jwt = createGoogleJwt(
                true,
                null,
                "sachini@example.com",
                "Sachini"
        );

        when(googleJwtDecoder.decode("missing-subject-token"))
                .thenReturn(jwt);

        assertThatThrownBy(() ->
                googleTokenService.validateAndExtractUserInfo(
                        "missing-subject-token"
                )
        ).isInstanceOf(InvalidGoogleTokenException.class)
                .hasMessage("Google ID token is missing required user information");
    }

    @Test
    void shouldRejectTokenWithMissingEmailVerifiedClaim() {

        // Arrange
        Jwt jwt = Jwt.withTokenValue("test-token")
                .header("alg", "RS256")
                .subject("google-sub-123")
                .claim("email", "sachini@example.com")
                .claim("name", "Sachini")
                // Intentionally no email_verified claim
                .build();

        when(googleJwtDecoder.decode("missing-email-verified-token"))
                .thenReturn(jwt);

        // Act & Assert
        assertThatThrownBy(() ->
                googleTokenService.validateAndExtractUserInfo(
                        "missing-email-verified-token"
                )
        ).isInstanceOf(InvalidGoogleTokenException.class)
                .hasMessage("Google email is not verified");
    }

    @Test
    void shouldRejectTokenWithBlankSubject() {

        Jwt jwt = createGoogleJwt(
                true,
                "   ",
                "sachini@example.com",
                "Sachini"
        );

        when(googleJwtDecoder.decode("blank-subject-token"))
                .thenReturn(jwt);

        assertThatThrownBy(() ->
                googleTokenService.validateAndExtractUserInfo("blank-subject-token")
        ).isInstanceOf(InvalidGoogleTokenException.class)
                .hasMessage("Google ID token is missing required user information");
    }

    @Test
    void shouldRejectTokenWithBlankEmail() {

        Jwt jwt = createGoogleJwt(
                true,
                "google-sub-123",
                "   ",
                "Sachini"
        );

        when(googleJwtDecoder.decode("blank-email-token"))
                .thenReturn(jwt);

        assertThatThrownBy(() ->
                googleTokenService.validateAndExtractUserInfo("blank-email-token")
        ).isInstanceOf(InvalidGoogleTokenException.class)
                .hasMessage("Google ID token is missing required user information");
    }

    @Test
    void shouldRejectTokenWithBlankName() {

        Jwt jwt = createGoogleJwt(
                true,
                "google-sub-123",
                "sachini@example.com",
                "   "
        );

        when(googleJwtDecoder.decode("blank-name-token"))
                .thenReturn(jwt);

        assertThatThrownBy(() ->
                googleTokenService.validateAndExtractUserInfo("blank-name-token")
        ).isInstanceOf(InvalidGoogleTokenException.class)
                .hasMessage("Google ID token is missing required user information");
    }

    private Jwt createGoogleJwt(
            boolean emailVerified,
            String subject,
            String email,
            String name
    ) {

        Jwt.Builder builder = Jwt.withTokenValue("test-token")
                .header("alg", "RS256")
                .claim("email_verified", emailVerified);

        if (subject != null) {
            builder.subject(subject);
        }

        if (email != null) {
            builder.claim("email", email);
        }

        if (name != null) {
            builder.claim("name", name);
        }

        return builder.build();
    }
}
