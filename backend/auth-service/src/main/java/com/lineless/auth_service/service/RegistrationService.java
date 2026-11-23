package com.lineless.auth_service.service;

import com.lineless.auth_service.dto.request.RegisterRequest;
import com.lineless.auth_service.entity.User;
import com.lineless.auth_service.entity.UserRole;
import com.lineless.auth_service.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.Locale;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class RegistrationService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    public Optional<User> register(RegisterRequest request) {

        String normalizedEmail = request.email()
                .trim()
                .toLowerCase(Locale.ROOT);

        if (userRepository.existsByEmail(normalizedEmail)) {
            return Optional.empty();
        }

        User user = new User();
        user.setName(request.name().trim());
        user.setEmail(normalizedEmail);
        user.setPasswordHash(
                passwordEncoder.encode(request.password())
        );
        user.setRole(UserRole.USER);

        try {
            return Optional.of(userRepository.saveAndFlush(user));

        } catch (DataIntegrityViolationException exception) {

            // Another request may have registered this email
            // between our existence check and database insertion.
            if (userRepository.existsByEmail(normalizedEmail)) {
                return Optional.empty();
            }

            // The database error was caused by something else.
            throw exception;
        }
    }
}
