package com.lineless.auth_service.service;

import com.lineless.auth_service.dto.request.LoginRequest;
import com.lineless.auth_service.entity.User;
import com.lineless.auth_service.entity.UserStatus;
import com.lineless.auth_service.exception.EmailNotVerifiedException;
import com.lineless.auth_service.exception.InvalidCredentialsException;
import com.lineless.auth_service.exception.RateLimitExceededException;
import com.lineless.auth_service.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class LoginServiceTest {

    private static final String EMAIL = "jane@example.com";
    private static final String CLIENT_IP = "192.168.1.10";
    private final BCryptPasswordEncoder passwordEncoder =
            new BCryptPasswordEncoder();
    @Mock
    private UserRepository userRepository;
    @Mock
    private LoginAttemptRateLimitService loginAttemptRateLimitService;
    private LoginService loginService;

    @BeforeEach
    void setUp() {
        loginService = new LoginService(
                userRepository,
                passwordEncoder,
                loginAttemptRateLimitService
        );
    }

    @Test
    void logsInWithCorrectCredentialsAndVerifiedEmail() {

        User user = activeUser();
        allowLoginAttempts();

        when(userRepository.findByEmail(EMAIL))
                .thenReturn(Optional.of(user));

        User result = loginService.login(
                new LoginRequest(EMAIL, "password123"), CLIENT_IP);

        assertThat(result).isSameAs(user);

        verify(loginAttemptRateLimitService)
                .resetEmailAttempts(EMAIL);
    }

    @Test
    void rejectsLoginWhenEmailDoesNotExist() {

        allowLoginAttempts();

        when(userRepository.findByEmail(EMAIL))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> loginService.login(
                new LoginRequest(EMAIL, "password123"), CLIENT_IP))
                .isInstanceOf(InvalidCredentialsException.class);

        verify(loginAttemptRateLimitService)
                .recordFailedAttempt(EMAIL, CLIENT_IP);
    }

    @Test
    void rejectsLoginWhenPasswordIsIncorrect() {

        User user = activeUser();
        allowLoginAttempts();

        when(userRepository.findByEmail(EMAIL))
                .thenReturn(Optional.of(user));

        assertThatThrownBy(() -> loginService.login(
                new LoginRequest(EMAIL, "wrong-password"), CLIENT_IP))
                .isInstanceOf(InvalidCredentialsException.class);

        verify(loginAttemptRateLimitService)
                .recordFailedAttempt(EMAIL, CLIENT_IP);
    }

    @Test
    void rejectsLoginWhenAccountIsInactive() {

        User user = activeUser();
        user.setStatus(UserStatus.INACTIVE);
        allowLoginAttempts();

        when(userRepository.findByEmail(EMAIL))
                .thenReturn(Optional.of(user));

        assertThatThrownBy(() -> loginService.login(
                new LoginRequest(EMAIL, "password123"), CLIENT_IP))
                .isInstanceOf(InvalidCredentialsException.class);

        verify(loginAttemptRateLimitService)
                .resetEmailAttempts(EMAIL);

        verify(loginAttemptRateLimitService, never())
                .recordFailedAttempt(anyString(), anyString());
    }

    @Test
    void rejectsLoginWhenEmailIsNotVerified() {

        User user = activeUser();
        user.setEmailVerified(false);
        allowLoginAttempts();

        when(userRepository.findByEmail(EMAIL))
                .thenReturn(Optional.of(user));

        assertThatThrownBy(() -> loginService.login(
                new LoginRequest(EMAIL, "password123"), CLIENT_IP))
                .isInstanceOf(EmailNotVerifiedException.class);

        verify(loginAttemptRateLimitService)
                .resetEmailAttempts(EMAIL);
    }

    @Test
    void rejectsLoginWhenUserHasNoPasswordHash() {

        User user = activeUser();
        user.setPasswordHash(null);
        allowLoginAttempts();

        when(userRepository.findByEmail(EMAIL))
                .thenReturn(Optional.of(user));

        assertThatThrownBy(() -> loginService.login(
                new LoginRequest(EMAIL, "password123"), CLIENT_IP))
                .isInstanceOf(InvalidCredentialsException.class);

        verify(loginAttemptRateLimitService)
                .recordFailedAttempt(EMAIL, CLIENT_IP);
    }

    @Test
    void rejectsLoginWhenRateLimitIsReached() {

        when(loginAttemptRateLimitService.isAllowed(EMAIL, CLIENT_IP))
                .thenReturn(false);

        assertThatThrownBy(() -> loginService.login(
                new LoginRequest(EMAIL, "password123"), CLIENT_IP))
                .isInstanceOf(RateLimitExceededException.class);

        verifyNoInteractions(userRepository);

        verify(loginAttemptRateLimitService, never())
                .recordFailedAttempt(anyString(), anyString());
    }

    @Test
    void normalizesEmailBeforeCheckingRateLimitAndFindingUser() {

        User user = activeUser();

        when(loginAttemptRateLimitService.isAllowed(EMAIL, CLIENT_IP))
                .thenReturn(true);

        when(userRepository.findByEmail(EMAIL))
                .thenReturn(Optional.of(user));

        User result = loginService.login(
                new LoginRequest("  JANE@EXAMPLE.COM  ", "password123"),
                CLIENT_IP);

        assertThat(result).isSameAs(user);

        verify(loginAttemptRateLimitService)
                .isAllowed(EMAIL, CLIENT_IP);
    }

    private void allowLoginAttempts() {
        when(loginAttemptRateLimitService.isAllowed(EMAIL, CLIENT_IP))
                .thenReturn(true);
    }

    private User activeUser() {

        User user = new User();

        user.setName("Jane Smith");
        user.setEmail(EMAIL);
        user.setPasswordHash(passwordEncoder.encode("password123"));
        user.setStatus(UserStatus.ACTIVE);
        user.setEmailVerified(true);

        return user;
    }
}
