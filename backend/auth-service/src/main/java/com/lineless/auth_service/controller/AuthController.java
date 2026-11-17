package com.lineless.auth_service.controller;

import com.lineless.auth_service.service.*;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import com.lineless.auth_service.dto.request.RegisterRequest;
import com.lineless.auth_service.dto.request.LoginRequest;
import com.lineless.auth_service.dto.response.AuthResponse;
import com.lineless.auth_service.dto.response.UserResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
@Validated
public class AuthController {

    private final RegistrationService registrationService;
    private final LoginService loginService;
    private final JwtService jwtService;
    private final RefreshTokenService refreshTokenService;
    private final RefreshTokenCookieService refreshTokenCookieService;

    @Value("${app.auth.refresh-cookie.name}")
    private String refreshCookieName;

    @PostMapping("/register")
    public ResponseEntity<Void> register(
            @Valid @RequestBody RegisterRequest request) {

        registrationService.register(request);

        return ResponseEntity
                .status(HttpStatus.CREATED)
                .build();
    }


    @PostMapping("/login")
    public ResponseEntity<AuthResponse> login(
            @Valid @RequestBody LoginRequest request) {

        var user = loginService.login(request);

        // Generate application access token
        String accessToken = jwtService.generateAccessToken(user);

        // Create and store refresh token
        String refreshToken = refreshTokenService.createRefreshToken(user);

        AuthResponse response = new AuthResponse(
                accessToken,
                "Bearer",
                jwtService.getAccessTokenExpiration(),
                new UserResponse(
                        user.getId(),
                        user.getName(),
                        user.getEmail(),
                        user.getRole().name()
                )
        );

        // Add refresh token as an HttpOnly cookie
        var headers = new org.springframework.http.HttpHeaders();
        refreshTokenCookieService.addRefreshTokenCookie(headers, refreshToken);

        return ResponseEntity.ok()
                .headers(headers)
                .body(response);
    }



    @PostMapping("/logout")
    public ResponseEntity<Void> logout(HttpServletRequest request) {

        String refreshToken = null;

        if (request.getCookies() != null) {
            for (Cookie cookie : request.getCookies()) {
                if (refreshCookieName.equals(cookie.getName())) {
                    refreshToken = cookie.getValue();
                    break;
                }
            }
        }

        refreshTokenService.revokeRefreshToken(refreshToken);

        HttpHeaders headers = new HttpHeaders();
        refreshTokenCookieService.clearRefreshTokenCookie(headers);

        return ResponseEntity.noContent()
                .headers(headers)
                .build();
    }

}
