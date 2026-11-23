
package com.lineless.auth_service.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;

import static org.assertj.core.api.Assertions.assertThat;

class RefreshTokenCookieServiceTest {

    private RefreshTokenCookieService cookieService;

    @BeforeEach
    void setUp() {
        cookieService = new RefreshTokenCookieService(
                "lineless_refresh",
                "/api/auth",
                true,
                false,
                "Lax",
                604800
        );
    }

    @Test
    void shouldCreateRefreshTokenCookieWithCorrectSecuritySettings() {
        // Arrange
        HttpHeaders headers = new HttpHeaders();

        // Act
        cookieService.addRefreshTokenCookie(headers, "test-refresh-token");

        // Assert
        String cookie = headers.getFirst(HttpHeaders.SET_COOKIE);

        assertThat(cookie).contains("HttpOnly");
        assertThat(cookie).contains("Path=/api/auth");
        assertThat(cookie).contains("SameSite=Lax");
        assertThat(cookie).contains("Max-Age=604800");
    }

    @Test
    void shouldClearRefreshTokenCookie() {
        // Arrange
        HttpHeaders headers = new HttpHeaders();

        // Act
        cookieService.clearRefreshTokenCookie(headers);

        // Assert
        String cookie = headers.getFirst(HttpHeaders.SET_COOKIE);

        assertThat(cookie).contains("HttpOnly");
        assertThat(cookie).contains("Path=/api/auth");
        assertThat(cookie).contains("SameSite=Lax");
        assertThat(cookie).contains("Max-Age=0");
    }

    private ResponseCookie getCookie(HttpHeaders headers) {
        String setCookieHeader = headers.getFirst(HttpHeaders.SET_COOKIE);

        assertThat(setCookieHeader).isNotNull();

        return ResponseCookie.fromClientResponse(
                "lineless_refresh",
                setCookieHeader.substring("lineless_refresh=".length())
                        .split(";", 2)[0]
        ).build();
    }
}
