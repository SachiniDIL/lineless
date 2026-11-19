package com.lineless.auth_service.controller;

import com.lineless.auth_service.dto.response.RefreshTokenResult;
import com.lineless.auth_service.entity.User;
import com.lineless.auth_service.entity.UserRole;
import com.lineless.auth_service.entity.UserStatus;
import com.lineless.auth_service.exception.InvalidGoogleTokenException;
import com.lineless.auth_service.exception.InvalidRefreshTokenException;
import com.lineless.auth_service.exception.RefreshTokenReuseException;
import com.lineless.auth_service.service.*;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(
        controllers = AuthController.class,
        properties = "app.auth.refresh-cookie.name=lineless_refresh"
)
@Import(com.lineless.auth_service.config.SecurityConfig.class)
class AuthControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private RegistrationWorkflowService registrationWorkflowService;

    @MockitoBean
    private LoginService loginService;

    @MockitoBean
    private JwtService jwtService;

    @MockitoBean(name = "jwtDecoder")
    private JwtDecoder jwtDecoder;

    @MockitoBean
    private RefreshTokenService refreshTokenService;

    @MockitoBean
    private RefreshTokenCookieService refreshTokenCookieService;

    @MockitoBean
    private GoogleAuthenticationService googleAuthenticationService;

    @Test
    void shouldRegisterUserSuccessfully() throws Exception {

        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                {
                    "name": "Test User",
                    "email": "test@example.com",
                    "password": "Password123"
                }
                """))
                .andExpect(status().isCreated());

        verify(registrationWorkflowService).register(any());
    }

    @Test
    void shouldLoginSuccessfully() throws Exception {

        User user = new User();
        user.setId(UUID.randomUUID());
        user.setName("Test User");
        user.setEmail("test@example.com");
        user.setRole(UserRole.USER);

        when(loginService.login(any())).thenReturn(user);
        when(jwtService.generateAccessToken(user)).thenReturn("test-access-token");
        when(jwtService.getAccessTokenExpiration()).thenReturn(900L);
        when(refreshTokenService.createRefreshToken(user))
                .thenReturn("test-refresh-token");

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                    {
                        "email": "test@example.com",
                        "password": "Password123"
                    }
                    """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").value("test-access-token"))
                .andExpect(jsonPath("$.tokenType").value("Bearer"))
                .andExpect(jsonPath("$.expiresIn").value(900))
                .andExpect(jsonPath("$.user.email").value("test@example.com"))
                .andExpect(jsonPath("$.user.role").value("USER"))
                .andExpect(jsonPath("$.refreshToken").doesNotExist());

        verify(refreshTokenCookieService)
                .addRefreshTokenCookie(any(), eq("test-refresh-token"));
    }

    @Test
    void shouldLogoutSuccessfully() throws Exception {

        mockMvc.perform(post("/api/auth/logout")
                        .cookie(new Cookie("lineless_refresh", "test-refresh-token")))
                .andExpect(status().isNoContent());

        verify(refreshTokenService)
                .revokeRefreshToken("test-refresh-token");

        verify(refreshTokenCookieService)
                .clearRefreshTokenCookie(any());
    }


    @Test
    void shouldLoginWithGoogleSuccessfully() throws Exception {

        User user = new User();
        user.setId(UUID.randomUUID());
        user.setName("Test User");
        user.setEmail("test@example.com");
        user.setRole(UserRole.USER);

        when(googleAuthenticationService.authenticate("valid-google-token"))
                .thenReturn(user);

        when(jwtService.generateAccessToken(user))
                .thenReturn("test-access-token");

        when(jwtService.getAccessTokenExpiration())
                .thenReturn(900L);

        when(refreshTokenService.createRefreshToken(user))
                .thenReturn("test-refresh-token");

        mockMvc.perform(post("/api/auth/google")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                    {
                        "idToken": "valid-google-token"
                    }
                    """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").value("test-access-token"))
                .andExpect(jsonPath("$.tokenType").value("Bearer"))
                .andExpect(jsonPath("$.expiresIn").value(900))
                .andExpect(jsonPath("$.user.email").value("test@example.com"))
                .andExpect(jsonPath("$.user.role").value("USER"))
                .andExpect(jsonPath("$.refreshToken").doesNotExist());

        verify(googleAuthenticationService)
                .authenticate("valid-google-token");

        verify(refreshTokenCookieService)
                .addRefreshTokenCookie(any(HttpHeaders.class), eq("test-refresh-token"));
    }


    @Test
    void shouldRejectInvalidGoogleToken() throws Exception {

        when(googleAuthenticationService.authenticate("invalid-token"))
                .thenThrow(new InvalidGoogleTokenException("Invalid Google ID token"));

        mockMvc.perform(post("/api/auth/google")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                    {
                        "idToken": "invalid-token"
                    }
                    """))
                .andExpect(status().isUnauthorized());

        verify(refreshTokenService, never()).createRefreshToken(any());
    }


    @Test
    void shouldRejectGoogleLoginWithEmptyIdToken() throws Exception {

        mockMvc.perform(post("/api/auth/google")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                    {
                        "idToken": ""
                    }
                    """))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(googleAuthenticationService);
    }

    @Test
    void shouldRefreshTokensSuccessfully() throws Exception {

        User user = new User();
        user.setId(UUID.randomUUID());
        user.setName("Test User");
        user.setEmail("test@example.com");
        user.setRole(UserRole.USER);
        user.setStatus(UserStatus.ACTIVE);

        RefreshTokenResult refreshResult = new RefreshTokenResult(
                "new-refresh-token",
                user
        );

        when(refreshTokenService.rotateRefreshToken("old-refresh-token"))
                .thenReturn(refreshResult);

        when(jwtService.generateAccessToken(user))
                .thenReturn("new-access-token");

        when(jwtService.getAccessTokenExpiration())
                .thenReturn(900L);

        mockMvc.perform(post("/api/auth/refresh")
                        .cookie(new Cookie("lineless_refresh", "old-refresh-token")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").value("new-access-token"))
                .andExpect(jsonPath("$.tokenType").value("Bearer"))
                .andExpect(jsonPath("$.expiresIn").value(900))
                .andExpect(jsonPath("$.user.email").value("test@example.com"))
                .andExpect(jsonPath("$.user.role").value("USER"))
                .andExpect(jsonPath("$.refreshToken").doesNotExist());

        verify(refreshTokenService).rotateRefreshToken("old-refresh-token");

        verify(refreshTokenCookieService).addRefreshTokenCookie(
                any(HttpHeaders.class),
                eq("new-refresh-token")
        );
    }

    @Test
    void shouldRejectRefreshWhenCookieIsMissing() throws Exception {

        when(refreshTokenService.rotateRefreshToken(null))
                .thenThrow(new InvalidRefreshTokenException(
                        "Invalid refresh token"
                ));

        mockMvc.perform(post("/api/auth/refresh"))
                .andExpect(status().isUnauthorized());

        verify(refreshTokenService).rotateRefreshToken(null);

        verify(jwtService, never()).generateAccessToken(any());

        verify(refreshTokenCookieService, never())
                .addRefreshTokenCookie(any(HttpHeaders.class), anyString());
    }

    @Test
    void shouldRejectInvalidRefreshToken() throws Exception {

        when(refreshTokenService.rotateRefreshToken("invalid-token"))
                .thenThrow(new InvalidRefreshTokenException(
                        "Invalid refresh token"
                ));

        mockMvc.perform(post("/api/auth/refresh")
                        .cookie(new Cookie("lineless_refresh", "invalid-token")))
                .andExpect(status().isUnauthorized());

        verify(refreshTokenService).rotateRefreshToken("invalid-token");

        verify(jwtService, never()).generateAccessToken(any());

        verify(refreshTokenCookieService, never())
                .addRefreshTokenCookie(any(HttpHeaders.class), anyString());
    }

    @Test
    void shouldRejectReusedRefreshToken() throws Exception {

        when(refreshTokenService.rotateRefreshToken("reused-token"))
                .thenThrow(new RefreshTokenReuseException(
                        "Refresh token reuse detected"
                ));

        mockMvc.perform(post("/api/auth/refresh")
                        .cookie(new Cookie("lineless_refresh", "reused-token")))
                .andExpect(status().isUnauthorized());

        verify(refreshTokenService).rotateRefreshToken("reused-token");

        verify(jwtService, never()).generateAccessToken(any());

        verify(refreshTokenCookieService, never())
                .addRefreshTokenCookie(any(HttpHeaders.class), anyString());
    }
}
