package com.lineless.auth_service.config;

import com.lineless.auth_service.entity.User;
import com.lineless.auth_service.entity.UserRole;
import com.lineless.auth_service.entity.UserStatus;
import com.lineless.auth_service.repository.UserRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import org.hibernate.exception.ConstraintViolationException;

import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.Locale;

@Slf4j
@Component
public class AdminBootstrapRunner implements CommandLineRunner {

    private static final int MAX_NAME_LENGTH = 255;
    private static final int MAX_EMAIL_LENGTH = 255;
    private static final int MIN_PASSWORD_LENGTH = 12;
    private static final Validator VALIDATOR =
            Validation.buildDefaultValidatorFactory().getValidator();

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    private final boolean enabled;
    private final String adminName;
    private final String adminEmail;
    private final String adminPassword;

    public AdminBootstrapRunner(
            UserRepository userRepository,
            PasswordEncoder passwordEncoder,
            @Value("${app.bootstrap.admin.enabled:false}") boolean enabled,
            @Value("${app.bootstrap.admin.name:}") String adminName,
            @Value("${app.bootstrap.admin.email:}") String adminEmail,
            @Value("${app.bootstrap.admin.password:}") String adminPassword
    ) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.enabled = enabled;
        this.adminName = adminName;
        this.adminEmail = adminEmail;
        this.adminPassword = adminPassword;
    }

    @Override
    public void run(String... args) {

        // 1. Check whether admin bootstrap is enabled.
        if (!enabled) {
            log.info("Admin bootstrap is disabled.");
            return;
        }

        // 2. Normalize text values used for validation and persistence.
        String normalizedName =
                adminName == null ? null : adminName.trim();
        String normalizedEmail =
                adminEmail == null
                        ? null
                        : adminEmail.trim().toLowerCase(Locale.ROOT);

        AdminBootstrapConfig config = new AdminBootstrapConfig(
                normalizedName,
                normalizedEmail,
                adminPassword
        );

        if (!VALIDATOR.validate(config).isEmpty()) {
            throw new IllegalStateException(
                    "Admin bootstrap configuration is invalid."
            );
        }

        // 3. Check whether an account with this email already exists.
        Optional<User> existingUser =
                userRepository.findByEmail(normalizedEmail);

        if (existingUser.isPresent()) {

            User existing = existingUser.get();

            if (existing.getRole() != UserRole.ADMIN) {
                log.warn(
                        "Admin bootstrap skipped. Account {} already exists with role {}. It was not promoted.",
                        normalizedEmail,
                        existing.getRole()
                );
            } else {
                log.info(
                        "Admin bootstrap skipped. Admin account {} already exists.",
                        normalizedEmail
                );
            }

            return;
        }

        // 4. Create the initial admin account.
        User admin = new User();

        admin.setName(normalizedName);
        admin.setEmail(normalizedEmail);
        admin.setPasswordHash(passwordEncoder.encode(adminPassword));
        admin.setRole(UserRole.ADMIN);
        admin.setStatus(UserStatus.ACTIVE);

        // Bootstrap admin email is trusted through server-side configuration.
        admin.setEmailVerified(true);
        admin.setEmailVerifiedAt(OffsetDateTime.now(ZoneOffset.UTC));

        try {
            userRepository.saveAndFlush(admin);
            log.info("Initial admin account created: {}", normalizedEmail);

        } catch (DataIntegrityViolationException exception) {
            if (!isUsersEmailConstraintViolation(exception)) {
                throw exception;
            }

            Optional<User> accountCreatedByAnotherInstance =
                    userRepository.findByEmail(normalizedEmail);

            if (accountCreatedByAnotherInstance.isPresent()) {
                log.info(
                        "Admin bootstrap skipped. Another instance created account {}.",
                        normalizedEmail
                );
                return;
            }

            throw exception;
        }
    }

    private boolean isUsersEmailConstraintViolation(
            DataIntegrityViolationException exception) {

        for (Throwable cause = exception; cause != null; cause = cause.getCause()) {
            if (cause instanceof ConstraintViolationException constraintViolation) {
                if ("users_email_key".equals(constraintViolation.getConstraintName())) {
                    return true;
                }
            }

        }

        return false;
    }

    private record AdminBootstrapConfig(
            @NotBlank
            @Size(max = MAX_NAME_LENGTH)
            String name,

            @NotBlank
            @Email
            @Size(max = MAX_EMAIL_LENGTH)
            String email,

            @NotBlank
            @Size(min = MIN_PASSWORD_LENGTH)
            String password
    ) {
    }
}