package com.lineless.auth_service.config;

import com.lineless.auth_service.entity.User;
import com.lineless.auth_service.entity.UserRole;
import com.lineless.auth_service.entity.UserStatus;
import com.lineless.auth_service.repository.UserRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.Locale;

@Slf4j
@Component
public class AdminBootstrapRunner implements CommandLineRunner {

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

        // 2. Validate the required configuration.
        if (adminName.isBlank()
                || adminEmail.isBlank()
                || adminPassword.isBlank()) {

            throw new IllegalStateException(
                    "Admin bootstrap is enabled, but required admin configuration is missing."
            );
        }

        // 3. Require a reasonably strong initial password.
        if (adminPassword.length() < 12) {
            throw new IllegalStateException(
                    "Initial admin password must contain at least 12 characters."
            );
        }

        // 4. Normalize the email.
        String normalizedEmail = adminEmail.trim().toLowerCase(Locale.ROOT);

        // 5. Check whether an account with this email already exists.
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

        // 6. Create the initial admin account.
        User admin = new User();

        admin.setName(adminName.trim());
        admin.setEmail(normalizedEmail);
        admin.setPasswordHash(passwordEncoder.encode(adminPassword));
        admin.setRole(UserRole.ADMIN);
        admin.setStatus(UserStatus.ACTIVE);

        // Bootstrap admin email is trusted through server-side configuration.
        admin.setEmailVerified(true);
        admin.setEmailVerifiedAt(OffsetDateTime.now(ZoneOffset.UTC));

        userRepository.save(admin);

        log.info("Initial admin account created: {}", normalizedEmail);
    }
}