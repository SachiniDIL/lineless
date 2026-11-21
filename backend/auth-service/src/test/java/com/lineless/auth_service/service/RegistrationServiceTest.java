
package com.lineless.auth_service.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import com.lineless.auth_service.dto.request.RegisterRequest;
import com.lineless.auth_service.entity.User;
import com.lineless.auth_service.entity.UserRole;
import com.lineless.auth_service.repository.UserRepository;

@ExtendWith(MockitoExtension.class)
class RegistrationServiceTest {

    @Mock
    private UserRepository userRepository;

    private RegistrationService registrationService;

    @BeforeEach
    void setUp() {
        registrationService = new RegistrationService(
                userRepository,
                new BCryptPasswordEncoder()
        );
    }

    @Test
    void registersUserSuccessfully() {

        RegisterRequest request = new RegisterRequest(
                "Jane Smith",
                "jane@example.com",
                "password123"
        );

        when(userRepository.existsByEmail("jane@example.com"))
                .thenReturn(false);

        when(userRepository.saveAndFlush(any(User.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        Optional<User> result = registrationService.register(request);

        assertThat(result).isPresent();

        User user = result.orElseThrow();

        assertThat(user.getName()).isEqualTo("Jane Smith");
        assertThat(user.getEmail()).isEqualTo("jane@example.com");

        verify(userRepository).saveAndFlush(any(User.class));
    }

    @Test
    void returnsEmptyWhenEmailAlreadyExists() {

        RegisterRequest request = new RegisterRequest(
                "Jane Smith",
                "jane@example.com",
                "password123"
        );

        when(userRepository.existsByEmail("jane@example.com"))
                .thenReturn(true);

        Optional<User> result = registrationService.register(request);

        assertThat(result).isEmpty();

        verify(userRepository, never()).saveAndFlush(any(User.class));
    }

    @Test
    void hashesPasswordUsingBcrypt() {

        RegisterRequest request = new RegisterRequest(
                "Jane Smith",
                "jane@example.com",
                "password123"
        );

        when(userRepository.existsByEmail("jane@example.com"))
                .thenReturn(false);

        when(userRepository.saveAndFlush(any(User.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        User user = registrationService.register(request).orElseThrow();

        assertThat(user.getPasswordHash())
                .isNotEqualTo(request.password())
                .startsWith("$2");

        assertThat(new BCryptPasswordEncoder()
                .matches(request.password(), user.getPasswordHash()))
                .isTrue();
    }

    @Test
    void assignsUserRoleToNewUsers() {

        RegisterRequest request = new RegisterRequest(
                "Jane Smith",
                "jane@example.com",
                "password123"
        );

        when(userRepository.existsByEmail("jane@example.com"))
                .thenReturn(false);

        when(userRepository.saveAndFlush(any(User.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        User user = registrationService.register(request).orElseThrow();

        assertThat(user.getRole()).isEqualTo(UserRole.USER);
    }
}
