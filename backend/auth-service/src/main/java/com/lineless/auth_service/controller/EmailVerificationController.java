package com.lineless.auth_service.controller;

import com.lineless.auth_service.dto.request.ResendVerificationRequest;
import com.lineless.auth_service.service.ResendVerificationService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.lineless.auth_service.dto.request.VerifyEmailRequest;
import com.lineless.auth_service.service.EmailVerificationService;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

import java.util.Map;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class EmailVerificationController {

    private final EmailVerificationService emailVerificationService;
    private final ResendVerificationService resendVerificationService;

    @PostMapping("/verify-email")
    public ResponseEntity<Void> verifyEmail(
            @Valid @RequestBody VerifyEmailRequest request) {

        emailVerificationService.verifyEmail(request.token());

        return ResponseEntity.ok().build();
    }

    @PostMapping("/resend-verification")
    public ResponseEntity<Map<String, String>> resendVerification(
            @Valid @RequestBody ResendVerificationRequest request,
            HttpServletRequest httpRequest) {

        String clientIp = httpRequest.getRemoteAddr();

        resendVerificationService.resend(request.email(), clientIp);

        return ResponseEntity.ok(Map.of(
                "message",
                "If an unverified account exists with this email, a verification email has been sent."
        ));
    }
}