package com.lineless.auth_service.config;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Bean;
import org.springframework.security.oauth2.jwt.BadJwtException;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.test.web.servlet.MockMvc;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;

import java.time.Instant;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = SecurityConfigTest.ProtectedTestController.class)
@AutoConfigureMockMvc(addFilters = true)
@Import({
        SecurityConfig.class,
        SecurityConfigTest.TestControllerConfig.class
})
class SecurityConfigTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean(name = "jwtDecoder")
    private JwtDecoder jwtDecoder;

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
    }
}
