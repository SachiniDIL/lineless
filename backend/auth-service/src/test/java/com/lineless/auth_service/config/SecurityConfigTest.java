package com.lineless.auth_service.config;

import com.lineless.auth_service.controller.CsrfController;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.security.oauth2.jwt.BadJwtException;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.web.FilterChainProxy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.csrf.CsrfFilter;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(controllers = SecurityConfigTest.ProtectedTestController.class)
@AutoConfigureMockMvc(addFilters = true)
@Import({
        SecurityConfig.class,
        CorsConfig.class,
        CsrfController.class,
        SecurityConfigTest.TestControllerConfig.class
})
@TestPropertySource(properties = {
        "app.cors.allowed-origins=http://localhost:3000"
})
class SecurityConfigTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean(name = "jwtDecoder")
    private JwtDecoder jwtDecoder;

    @Autowired
    private ApplicationContext applicationContext;

    @Autowired
    private FilterChainProxy filterChainProxy;

    @Test
    void shouldRejectRequestToProtectedEndpointWithoutToken() throws Exception {

        mockMvc.perform(get("/api/protected"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void shouldAllowRequestWithValidJwt() throws Exception {

        Instant now = Instant.now();

        Jwt jwt = Jwt.withTokenValue("valid-token")
                .header("alg", "RS256")
                .subject("test-user-id")
                .issuedAt(now)
                .expiresAt(now.plusSeconds(900))
                .build();

        when(jwtDecoder.decode("valid-token")).thenReturn(jwt);

        mockMvc.perform(
                        get("/api/protected")
                                .header("Authorization", "Bearer valid-token")
                ).andExpect(status().isOk())
                .andExpect(content().string("Authenticated successfully"));
    }

    @Test
    void shouldRejectRequestWithInvalidJwt() throws Exception {

        when(jwtDecoder.decode("invalid-token"))
                .thenThrow(new BadJwtException("Invalid JWT"));

        mockMvc.perform(
                get("/api/protected")
                        .header("Authorization", "Bearer invalid-token")
        ).andExpect(status().isUnauthorized());
    }

    @Test
    void shouldRejectPostRequestWithoutCsrfToken() throws Exception {

        mockMvc.perform(post("/api/protected"))
                .andExpect(status().isForbidden());
    }

    @Test
    void shouldAllowPostRequestWithValidCsrfToken() throws Exception {

        String token = "test-csrf-token";

        mockMvc.perform(
                        post("/api/protected")
                                .cookie(new Cookie("XSRF-TOKEN", token))
                                .header("X-XSRF-TOKEN", token)
                )
                .andExpect(status().isUnauthorized());
    }

    @Test
    void shouldReturnCsrfTokenAndSetCookie() throws Exception {
        mockMvc.perform(get("/api/auth/csrf"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").isNotEmpty())
                .andExpect(cookie().exists("XSRF-TOKEN"));
    }

    @Test
    void shouldPrintSecurityFilterChains() {

        applicationContext.getBeansOfType(SecurityFilterChain.class)
                .forEach((name, chain) ->
                        System.out.println("Bean: " + name + " -> " + chain)
                );

        filterChainProxy.getFilterChains().forEach(chain -> {
            chain.getFilters().forEach(filter ->
                    System.out.println(filter.getClass().getName())
            );
        });
    }

    @Test
    void shouldPrintActualCsrfTokenRepository() {

        filterChainProxy.getFilterChains().forEach(chain -> {

            chain.getFilters().stream()
                    .filter(CsrfFilter.class::isInstance)
                    .map(CsrfFilter.class::cast)
                    .forEach(csrfFilter -> {

                        Object repository = ReflectionTestUtils.getField(
                                csrfFilter,
                                "tokenRepository"
                        );

                        System.out.println(
                                "Actual CSRF repository: "
                                        + repository.getClass().getName()
                        );
                    });
        });
    }

    @Test
    void shouldAllowCorsRequestFromFrontendOrigin() throws Exception {

        mockMvc.perform(
                        options("/api/auth/login")
                                .header("Origin", "http://localhost:3000")
                                .header("Access-Control-Request-Method", "POST")
                                .header("Access-Control-Request-Headers", "Content-Type")
                )
                .andExpect(status().isOk())
                .andExpect(header().string(
                        "Access-Control-Allow-Origin",
                        "http://localhost:3000"
                ))
                .andExpect(header().string(
                        "Access-Control-Allow-Credentials",
                        "true"
                ));
    }

    @Test
    void shouldRejectCorsRequestFromUnknownOrigin() throws Exception {

        mockMvc.perform(
                        options("/api/auth/login")
                                .header("Origin", "http://malicious-site.com")
                                .header("Access-Control-Request-Method", "POST")
                                .header("Access-Control-Request-Headers", "Content-Type")
                )
                .andExpect(header().doesNotExist("Access-Control-Allow-Origin"));
    }

    @Test
    void shouldAllowHealthProbeEndpointsWithoutToken() throws Exception {
        mockMvc.perform(get("/actuator/health"))
                .andExpect(status().isNotFound());
        mockMvc.perform(get("/actuator/health/liveness"))
                .andExpect(status().isNotFound());
        mockMvc.perform(get("/actuator/health/readiness"))
                .andExpect(status().isNotFound());
    }

    @Test
    void shouldProtectDetailedHealthEndpoints() throws Exception {
        mockMvc.perform(get("/actuator/health/db"))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(get("/actuator/health/redis"))
                .andExpect(status().isUnauthorized());
    }

    @TestConfiguration
    static class TestControllerConfig {

        @Bean
        ProtectedTestController protectedTestController() {
            return new ProtectedTestController();
        }
    }

    @RestController
    static class ProtectedTestController {

        @GetMapping("/api/protected")
        String protectedEndpoint() {
            return "Authenticated successfully";
        }

        @PostMapping("/api/protected")
        String protectedPostEndpoint() {
            return "POST successful";
        }
    }
}
