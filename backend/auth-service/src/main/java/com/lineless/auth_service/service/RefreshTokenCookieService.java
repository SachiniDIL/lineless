package com.lineless.auth_service.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Service;

@Service
public class RefreshTokenCookieService {

    private final String cookieName;
    private final String cookiePath;
    private final boolean httpOnly;
    private final boolean secure;
    private final String sameSite;
    private final long refreshTokenExpiration;

    public RefreshTokenCookieService(
            @Value("${app.auth.refresh-cookie.name}") String cookieName,
            @Value("${app.auth.refresh-cookie.path}") String cookiePath,
            @Value("${app.auth.refresh-cookie.http-only}") boolean httpOnly,
            @Value("${app.auth.refresh-cookie.secure}") boolean secure,
            @Value("${app.auth.refresh-cookie.same-site}") String sameSite,
            @Value("${app.jwt.refresh-token-expiration}") long refreshTokenExpiration) {

        this.cookieName = cookieName;
        this.cookiePath = cookiePath;
        this.httpOnly = httpOnly;
        this.secure = secure;
        this.sameSite = sameSite;
        this.refreshTokenExpiration = refreshTokenExpiration;
    }

    public void addRefreshTokenCookie(
            HttpHeaders headers,
            String refreshToken) {

        ResponseCookie cookie = ResponseCookie.from(cookieName, refreshToken)
                .httpOnly(httpOnly)
                .secure(secure)
                .path(cookiePath)
                .sameSite(sameSite)
                .maxAge(refreshTokenExpiration)
                .build();

        headers.add(HttpHeaders.SET_COOKIE, cookie.toString());
    }

    public void clearRefreshTokenCookie(HttpHeaders headers) {

        ResponseCookie cookie = ResponseCookie.from(cookieName, "")
                .httpOnly(httpOnly)
                .secure(secure)
                .path(cookiePath)
                .sameSite(sameSite)
                .maxAge(0)
                .build();

        headers.add(HttpHeaders.SET_COOKIE, cookie.toString());
    }
}
