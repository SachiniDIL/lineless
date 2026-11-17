package com.lineless.auth_service.controller;

import com.lineless.auth_service.entity.User;
import com.lineless.auth_service.entity.UserRole;
import com.lineless.auth_service.service.JwtService;
import com.lineless.auth_service.service.LoginService;
import com.lineless.auth_service.service.RefreshTokenCookieService;
import com.lineless.auth_service.service.RefreshTokenService;
import com.lineless.auth_service.service.RegistrationService;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
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
    private RegistrationService registrationService;

    @MockitoBean
    private LoginService loginService;

    @MockitoBean
    private JwtService jwtService;

    @MockitoBean
    private RefreshTokenService refreshTokenService;

    @MockitoBean
    private RefreshTokenCookieService refreshTokenCookieService;

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

        verify(registrationService).register(any());
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
}
