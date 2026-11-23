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
import org.hibernate.exception.ConstraintViolationException;
import org.springframework.dao.DataIntegrityViolationException;

import java.util.Optional;
import java.sql.SQLException;

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
        verify(userRepository, never()).saveAndFlush(any(User.class));
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
        verify(userRepository, never()).saveAndFlush(any(User.class));
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
        verify(userRepository, never()).saveAndFlush(any(User.class));
    }

    @Test
    void shouldRejectMissingAdminEmail() {
        AdminBootstrapRunner runner = new AdminBootstrapRunner(
                userRepository,
                passwordEncoder,
                true,
                "LineLess Admin",
                "",
                "StrongPassword123!"
        );

        assertThrows(IllegalStateException.class, runner::run);

        verify(userRepository, never()).findByEmail(anyString());
        verify(userRepository, never()).saveAndFlush(any(User.class));
    }

    @Test
    void shouldRejectMissingAdminPassword() {
        AdminBootstrapRunner runner = new AdminBootstrapRunner(
                userRepository,
                passwordEncoder,
                true,
                "LineLess Admin",
                "admin@lineless.local",
                ""
        );

        assertThrows(IllegalStateException.class, runner::run);

        verify(userRepository, never()).findByEmail(anyString());
        verify(userRepository, never()).saveAndFlush(any(User.class));
    }

    @Test
    void shouldRejectInvalidAdminEmailFormat() {
        AdminBootstrapRunner runner = new AdminBootstrapRunner(
                userRepository,
                passwordEncoder,
                true,
                "LineLess Admin",
                "invalid-email",
                "StrongPassword123!"
        );

        assertThrows(IllegalStateException.class, runner::run);

        verify(userRepository, never()).findByEmail(anyString());
        verify(userRepository, never()).saveAndFlush(any(User.class));
    }

    @Test
    void shouldRejectEmailLongerThan255Characters() {
        String longEmail = "a".repeat(244) + "@example.com";

        AdminBootstrapRunner runner = new AdminBootstrapRunner(
                userRepository,
                passwordEncoder,
                true,
                "LineLess Admin",
                longEmail,
                "StrongPassword123!"
        );

        assertThrows(IllegalStateException.class, runner::run);

        verify(userRepository, never()).findByEmail(anyString());
        verify(userRepository, never()).saveAndFlush(any(User.class));
    }

    @Test
    void shouldRejectNameLongerThan255Characters() {
        AdminBootstrapRunner runner = new AdminBootstrapRunner(
                userRepository,
                passwordEncoder,
                true,
                "A".repeat(256),
                "admin@lineless.local",
                "StrongPassword123!"
        );

        assertThrows(IllegalStateException.class, runner::run);

        verify(userRepository, never()).findByEmail(anyString());
        verify(userRepository, never()).saveAndFlush(any(User.class));
    }

    @Test
    void shouldRejectWhitespaceOnlyName() {
        AdminBootstrapRunner runner = new AdminBootstrapRunner(
                userRepository,
                passwordEncoder,
                true,
                "   ",
                "admin@lineless.local",
                "StrongPassword123!"
        );

        assertThrows(IllegalStateException.class, runner::run);

        verify(userRepository, never()).findByEmail(anyString());
        verify(userRepository, never()).saveAndFlush(any(User.class));
    }

    @Test
    void shouldRejectWhitespaceOnlyPassword() {
        AdminBootstrapRunner runner = new AdminBootstrapRunner(
                userRepository,
                passwordEncoder,
                true,
                "LineLess Admin",
                "admin@lineless.local",
                "            "
        );

        assertThrows(IllegalStateException.class, runner::run);

        verify(userRepository, never()).findByEmail(anyString());
        verify(userRepository, never()).saveAndFlush(any(User.class));
    }

    @Test
    void shouldTrimNameAndNormalizeEmail() {
        when(userRepository.findByEmail("admin@lineless.local"))
                .thenReturn(Optional.empty());

        AdminBootstrapRunner runner = new AdminBootstrapRunner(
                userRepository,
                passwordEncoder,
                true,
                "  LineLess Admin  ",
                "  ADMIN@LINELESS.LOCAL  ",
                "StrongPassword123!"
        );

        runner.run();

        ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).saveAndFlush(captor.capture());

        assertEquals("LineLess Admin", captor.getValue().getName());
        assertEquals("admin@lineless.local", captor.getValue().getEmail());
    }

    @Test
    void shouldAcceptPasswordContainingSpaces() {
        String password = "Admin  Password123";
        when(userRepository.findByEmail("admin@lineless.local"))
                .thenReturn(Optional.empty());

        AdminBootstrapRunner runner = new AdminBootstrapRunner(
                userRepository,
                passwordEncoder,
                true,
                "LineLess Admin",
                "admin@lineless.local",
                password
        );

        runner.run();

        ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).saveAndFlush(captor.capture());
        assertTrue(passwordEncoder.matches(password, captor.getValue().getPasswordHash()));
    }

    @Test
    void shouldAcceptPasswordOfExactly12Characters() {
        String password = "123456789012";
        when(userRepository.findByEmail("admin@lineless.local"))
                .thenReturn(Optional.empty());

        AdminBootstrapRunner runner = new AdminBootstrapRunner(
                userRepository,
                passwordEncoder,
                true,
                "LineLess Admin",
                "admin@lineless.local",
                password
        );

        runner.run();

        ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).saveAndFlush(captor.capture());
        assertTrue(passwordEncoder.matches(password, captor.getValue().getPasswordHash()));
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

        verify(userRepository, never()).saveAndFlush(any(User.class));
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

        verify(userRepository, never()).saveAndFlush(any(User.class));
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

        verify(userRepository).saveAndFlush(userCaptor.capture());

        User savedUser = userCaptor.getValue();

        assertEquals("LineLess Admin", savedUser.getName());
        assertEquals("admin@lineless.local", savedUser.getEmail());
        assertEquals(UserRole.ADMIN, savedUser.getRole());
        assertEquals(UserStatus.ACTIVE, savedUser.getStatus());
        assertTrue(savedUser.isEmailVerified());
        assertNotNull(savedUser.getEmailVerifiedAt());
        assertTrue(
                passwordEncoder.matches(
                        "StrongPassword123!",
                        savedUser.getPasswordHash()
                )
        );
    }

    @Test
    void shouldSkipDuplicateEmailViolationWhenAnotherAdminWasCreated() {
        User existingAdmin = new User();
        existingAdmin.setName("Existing Admin");
        existingAdmin.setEmail("admin@lineless.local");
        existingAdmin.setRole(UserRole.ADMIN);
        existingAdmin.setStatus(UserStatus.ACTIVE);
        existingAdmin.setPasswordHash("existing-hash");

        when(userRepository.findByEmail("admin@lineless.local"))
                .thenReturn(Optional.empty(), Optional.of(existingAdmin));
        when(userRepository.saveAndFlush(any(User.class)))
                .thenThrow(emailConstraintViolation());

        AdminBootstrapRunner runner = new AdminBootstrapRunner(
                userRepository,
                passwordEncoder,
                true,
                "Configured Admin",
                "ADMIN@LINELESS.LOCAL",
                "ConfiguredPassword123!"
        );

        assertDoesNotThrow(() -> runner.run());

        assertEquals("Existing Admin", existingAdmin.getName());
        assertEquals("Existing Admin", existingAdmin.getName());
        assertEquals(UserRole.ADMIN, existingAdmin.getRole());
        assertEquals("existing-hash", existingAdmin.getPasswordHash());
        verify(userRepository).saveAndFlush(any(User.class));
        verify(userRepository, times(2)).findByEmail("admin@lineless.local");
    }

    @Test
    void shouldSkipDuplicateEmailViolationWhenAnotherUserWasCreated() {
        User existingUser = new User();
        existingUser.setName("Existing User");
        existingUser.setEmail("admin@lineless.local");
        existingUser.setRole(UserRole.USER);
        existingUser.setStatus(UserStatus.ACTIVE);
        existingUser.setPasswordHash("existing-user-hash");

        when(userRepository.findByEmail("admin@lineless.local"))
                .thenReturn(Optional.empty(), Optional.of(existingUser));
        when(userRepository.saveAndFlush(any(User.class)))
                .thenThrow(emailConstraintViolation());

        AdminBootstrapRunner runner = new AdminBootstrapRunner(
                userRepository,
                passwordEncoder,
                true,
                "Configured Admin",
                "admin@lineless.local",
                "ConfiguredPassword123!"
        );

        assertDoesNotThrow(() -> runner.run());

        assertEquals(UserRole.USER, existingUser.getRole());
        assertEquals("Existing User", existingUser.getName());
        assertEquals("existing-user-hash", existingUser.getPasswordHash());
        verify(userRepository, times(2)).findByEmail("admin@lineless.local");
    }

    @Test
    void shouldRethrowDuplicateEmailViolationWhenNoAccountIsFound() {
        DataIntegrityViolationException violation = emailConstraintViolation();
        when(userRepository.findByEmail("admin@lineless.local"))
                .thenReturn(Optional.empty(), Optional.empty());
        when(userRepository.saveAndFlush(any(User.class)))
                .thenThrow(violation);

        AdminBootstrapRunner runner = new AdminBootstrapRunner(
                userRepository,
                passwordEncoder,
                true,
                "LineLess Admin",
                "admin@lineless.local",
                "StrongPassword123!"
        );

        assertSame(violation, assertThrows(
                DataIntegrityViolationException.class,
                runner::run
        ));
    }

    @Test
    void shouldRethrowUnrelatedConstraintViolation() {
        DataIntegrityViolationException violation =
                new DataIntegrityViolationException(
                        "Unrelated constraint violation",
                        new ConstraintViolationException(
                                "Unrelated constraint",
                                new SQLException("Unrelated constraint"),
                                "some_other_constraint"
                        )
                );
        when(userRepository.findByEmail("admin@lineless.local"))
                .thenReturn(Optional.empty());
        when(userRepository.saveAndFlush(any(User.class)))
                .thenThrow(violation);

        AdminBootstrapRunner runner = new AdminBootstrapRunner(
                userRepository,
                passwordEncoder,
                true,
                "LineLess Admin",
                "admin@lineless.local",
                "StrongPassword123!"
        );

        assertSame(violation, assertThrows(
                DataIntegrityViolationException.class,
                runner::run
        ));
        verify(userRepository, times(1)).findByEmail("admin@lineless.local");
    }

    private DataIntegrityViolationException emailConstraintViolation() {
        return new DataIntegrityViolationException(
                "Duplicate email",
                new ConstraintViolationException(
                        "Duplicate email",
                        new SQLException("Duplicate email"),
                        "users_email_key"
                )
        );
    }
}