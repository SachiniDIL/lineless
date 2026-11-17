package com.lineless.auth_service.controller;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.lineless.auth_service.dto.request.RegisterRequest;
import com.lineless.auth_service.dto.request.LoginRequest;
import com.lineless.auth_service.dto.response.AuthResponse;
import com.lineless.auth_service.dto.response.UserResponse;
import com.lineless.auth_service.service.RegistrationService;
import com.lineless.auth_service.service.LoginService;
import com.lineless.auth_service.service.JwtService;

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
        String accessToken = jwtService.generateAccessToken(user);

        AuthResponse response = new AuthResponse(
                accessToken,
                "Bearer",
                jwtService.getAccessTokenExpiration(),
                new UserResponse(
                        user.getId(),
                        user.getName(),
                        user.getEmail(),
                        user.getRole().name()));

        return ResponseEntity.ok(response);
    }
}
