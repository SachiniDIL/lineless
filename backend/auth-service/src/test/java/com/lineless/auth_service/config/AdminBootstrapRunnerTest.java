package com.lineless.auth_service.config;

import com.lineless.auth_service.entity.UserRole;
import com.lineless.auth_service.entity.UserStatus;
import com.lineless.auth_service.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import com.lineless.auth_service.entity.User;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

class AdminBootstrapRunnerTest {

    private UserRepository userRepository;
    private PasswordEncoder passwordEncoder;

    @BeforeEach
    void setUp() {
        userRepository = mock(UserRepository.class);
        passwordEncoder = new BCryptPasswordEncoder();
    }

    @Test
    void shouldNotCreateAdminWhenBootstrapIsDisabled() {

        AdminBootstrapRunner runner = new AdminBootstrapRunner(
                userRepository,
                passwordEncoder,
                false,
                "",
                "",
                ""
        );

        runner.run();

        verify(userRepository, never()).findByEmail(anyString());
        verify(userRepository, never()).save(any(User.class));
    }

    @Test
    void shouldThrowExceptionWhenAdminConfigurationIsMissing() {

        AdminBootstrapRunner runner = new AdminBootstrapRunner(
                userRepository,
                passwordEncoder,
                true,
                "",
                "admin@lineless.local",
                "StrongPassword123!"
        );

        assertThrows(
                IllegalStateException.class,
                runner::run
        );

        verify(userRepository, never()).findByEmail(anyString());
        verify(userRepository, never()).save(any(User.class));
    }

    @Test
    void shouldRejectWeakAdminPassword() {

        AdminBootstrapRunner runner = new AdminBootstrapRunner(
                userRepository,
                passwordEncoder,
                true,
                "LineLess Admin",
                "admin@lineless.local",
                "weakpass"
        );

        assertThrows(
                IllegalStateException.class,
                runner::run
        );

        verify(userRepository, never()).findByEmail(anyString());
        verify(userRepository, never()).save(any(User.class));
    }

    @Test
    void shouldNotPromoteExistingUserToAdmin() {

        User existingUser = new User();
        existingUser.setName("Existing User");
        existingUser.setEmail("admin@lineless.local");
        existingUser.setRole(UserRole.USER);
        existingUser.setStatus(UserStatus.ACTIVE);

        when(userRepository.findByEmail("admin@lineless.local"))
                .thenReturn(Optional.of(existingUser));

        AdminBootstrapRunner runner = new AdminBootstrapRunner(
                userRepository,
                passwordEncoder,
                true,
                "LineLess Admin",
                "admin@lineless.local",
                "StrongPassword123!"
        );

        runner.run();

        assertEquals(UserRole.USER, existingUser.getRole());

        verify(userRepository, never()).save(any(User.class));
    }

    @Test
    void shouldNotModifyExistingAdmin() {

        User existingAdmin = new User();
        existingAdmin.setName("Existing Admin");
        existingAdmin.setEmail("admin@lineless.local");
        existingAdmin.setRole(UserRole.ADMIN);
        existingAdmin.setStatus(UserStatus.ACTIVE);
        existingAdmin.setPasswordHash("existing-hashed-password");

        when(userRepository.findByEmail("admin@lineless.local"))
                .thenReturn(Optional.of(existingAdmin));

        AdminBootstrapRunner runner = new AdminBootstrapRunner(
                userRepository,
                passwordEncoder,
                true,
                "New Admin Name",
                "admin@lineless.local",
                "DifferentStrongPassword123!"
        );

        runner.run();

        assertEquals("Existing Admin", existingAdmin.getName());
        assertEquals(UserRole.ADMIN, existingAdmin.getRole());
        assertEquals("existing-hashed-password", existingAdmin.getPasswordHash());

        verify(userRepository, never()).save(any(User.class));
    }

    @Test
    void shouldCreateAdminWhenAccountDoesNotExist() {

        when(userRepository.findByEmail("admin@lineless.local"))
                .thenReturn(Optional.empty());

        AdminBootstrapRunner runner = new AdminBootstrapRunner(
                userRepository,
                passwordEncoder,
                true,
                "LineLess Admin",
                "ADMIN@LINELESS.LOCAL",
                "StrongPassword123!"
        );

        runner.run();

        ArgumentCaptor<User> userCaptor =
                ArgumentCaptor.forClass(User.class);

        verify(userRepository).save(userCaptor.capture());

        User savedUser = userCaptor.getValue();

        assertEquals("LineLess Admin", savedUser.getName());
        assertEquals("admin@lineless.local", savedUser.getEmail());
        assertEquals(UserRole.ADMIN, savedUser.getRole());
        assertEquals(UserStatus.ACTIVE, savedUser.getStatus());

        assertTrue(
                passwordEncoder.matches(
                        "StrongPassword123!",
                        savedUser.getPasswordHash()
                )
        );
    }
}