package com.lineless.auth_service.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import com.lineless.auth_service.dto.request.LoginRequest;
import com.lineless.auth_service.entity.User;
import com.lineless.auth_service.entity.UserStatus;
import com.lineless.auth_service.exception.InvalidCredentialsException;
import com.lineless.auth_service.repository.UserRepository;

@ExtendWith(MockitoExtension.class)
class LoginServiceTest {

    @Mock
    private UserRepository userRepository;

    private final BCryptPasswordEncoder passwordEncoder = new BCryptPasswordEncoder();
    private LoginService loginService;

    @BeforeEach
    void setUp() {
        loginService = new LoginService(userRepository, passwordEncoder);
    }

    @Test
    void logsInWithCorrectCredentials() {
        User user = activeUser();
        when(userRepository.findByEmail("jane@example.com"))
                .thenReturn(Optional.of(user));

        User result = loginService.login(
                new LoginRequest("jane@example.com", "password123"));

        assertThat(result).isSameAs(user);
        verify(userRepository).findByEmail("jane@example.com");
    }

    @Test
    void rejectsLoginWhenEmailDoesNotExist() {
        when(userRepository.findByEmail("missing@example.com"))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> loginService.login(
                new LoginRequest("missing@example.com", "password123")))
                .isInstanceOf(InvalidCredentialsException.class);
    }

    @Test
    void rejectsLoginWhenPasswordIsIncorrect() {
        User user = activeUser();
        when(userRepository.findByEmail("jane@example.com"))
                .thenReturn(Optional.of(user));

        assertThatThrownBy(() -> loginService.login(
                new LoginRequest("jane@example.com", "wrong-password")))
                .isInstanceOf(InvalidCredentialsException.class);
    }

    @Test
    void rejectsLoginWhenAccountIsInactive() {
        User user = activeUser();
        user.setStatus(UserStatus.INACTIVE);
        when(userRepository.findByEmail("jane@example.com"))
                .thenReturn(Optional.of(user));

        assertThatThrownBy(() -> loginService.login(
                new LoginRequest("jane@example.com", "password123")))
                .isInstanceOf(InvalidCredentialsException.class);
    }

    private User activeUser() {
        User user = new User();
        user.setName("Jane Smith");
        user.setEmail("jane@example.com");
        user.setPasswordHash(passwordEncoder.encode("password123"));
        user.setStatus(UserStatus.ACTIVE);
        return user;
    }
}
