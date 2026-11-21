package com.lineless.auth_service.exception;

import com.lineless.auth_service.dto.response.ApiErrorResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpServletRequest;

import static org.assertj.core.api.Assertions.assertThat;

class GlobalExceptionHandlerTest {

    private GlobalExceptionHandler globalExceptionHandler;
    private MockHttpServletRequest request;

    @BeforeEach
    void setUp() {
        globalExceptionHandler = new GlobalExceptionHandler();
        request = new MockHttpServletRequest();
    }

    @Test
    void returns503WhenRateLimitInfrastructureFails() {

        // Arrange
        RateLimitInfrastructureException exception =
                new RateLimitInfrastructureException(
                        new RuntimeException("Redis unavailable")
                );

        // Act
        ResponseEntity<ApiErrorResponse> response =
                globalExceptionHandler.handleRateLimitInfrastructure(
                        exception, request
                );

        // Assert
        assertThat(response.getStatusCode())
                .isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);

        assertThat(response.getBody()).isNotNull();

        assertThat(response.getBody().status()).isEqualTo(503);

        assertThat(response.getBody().error())
                .isEqualTo("Service Unavailable");

        assertThat(response.getBody().message())
                .isEqualTo(
                        "Rate limiting service is temporarily unavailable. Please try again later."
                );

        assertThat(response.getBody().timestamp()).isNotNull();
    }

    @Test
    void returns429WhenRateLimitIsExceeded() {

        // Arrange
        RateLimitExceededException exception =
                new RateLimitExceededException();

        // Act
        ResponseEntity<ApiErrorResponse> response =
                globalExceptionHandler.handleRateLimitExceeded(
                        exception, request
                );

        // Assert
        assertThat(response.getStatusCode())
                .isEqualTo(HttpStatus.TOO_MANY_REQUESTS);

        assertThat(response.getBody()).isNotNull();

        assertThat(response.getBody().status()).isEqualTo(429);

        assertThat(response.getBody().message())
                .isEqualTo(exception.getMessage());
    }
}
