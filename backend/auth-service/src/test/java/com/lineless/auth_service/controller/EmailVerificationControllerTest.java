package com.lineless.auth_service.controller;

import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import com.lineless.auth_service.config.SecurityConfig;
import com.lineless.auth_service.exception.InvalidEmailVerificationTokenException;
import com.lineless.auth_service.service.EmailVerificationService;
import com.lineless.auth_service.service.ResendVerificationService;

@WebMvcTest(EmailVerificationController.class)
@Import(SecurityConfig.class)
class EmailVerificationControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private EmailVerificationService emailVerificationService;

    @MockitoBean
    private ResendVerificationService resendVerificationService;

    @MockitoBean(name = "jwtDecoder")
    private JwtDecoder jwtDecoder;

    @Test
    void shouldVerifyEmailSuccessfully() throws Exception {

        mockMvc.perform(post("/api/auth/verify-email")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                    "token": "valid-verification-token"
                                }
                                """))
                .andExpect(status().isOk());

        verify(emailVerificationService)
                .verifyEmail("valid-verification-token");
    }

    @Test
    void shouldRejectInvalidVerificationToken() throws Exception {

        doThrow(new InvalidEmailVerificationTokenException())
                .when(emailVerificationService)
                .verifyEmail("invalid-token");

        mockMvc.perform(post("/api/auth/verify-email")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                    "token": "invalid-token"
                                }
                                """))
                .andExpect(status().isBadRequest());

        verify(emailVerificationService)
                .verifyEmail("invalid-token");
    }

    @Test
    void shouldRejectEmptyVerificationToken() throws Exception {

        mockMvc.perform(post("/api/auth/verify-email")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                    "token": ""
                                }
                                """))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(emailVerificationService);
    }

    @Test
    void shouldRejectMissingVerificationToken() throws Exception {

        mockMvc.perform(post("/api/auth/verify-email")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {}
                                """))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(emailVerificationService);
    }

    @Test
    void shouldResendVerificationEmailSuccessfully() throws Exception {

        mockMvc.perform(post("/api/auth/resend-verification")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                    "email": "jane@example.com"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value(
                        "If an unverified account exists with this email, a verification email has been sent."
                ));

        verify(resendVerificationService)
                .resend("jane@example.com");
    }

    @Test
    void shouldReturnSameResponseWhenResendingForUnknownEmail() throws Exception {

        mockMvc.perform(post("/api/auth/resend-verification")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                    "email": "unknown@example.com"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value(
                        "If an unverified account exists with this email, a verification email has been sent."
                ));

        verify(resendVerificationService)
                .resend("unknown@example.com");
    }

    @Test
    void shouldRejectInvalidEmailWhenResendingVerification() throws Exception {

        mockMvc.perform(post("/api/auth/resend-verification")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                    "email": "not-an-email"
                                }
                                """))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(resendVerificationService);
    }
}
