package com.lineless.auth_service.repository;

import com.lineless.auth_service.entity.User;
import com.lineless.auth_service.entity.UserStatus;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@AutoConfigureTestDatabase(
        replace = AutoConfigureTestDatabase.Replace.NONE
)
class UserRepositoryTest {

    @Autowired
    private UserRepository userRepository;

    @Test
    void shouldSaveAndFindUserByEmail() {

        // Create a user
        User user = new User();
        user.setName("John Smith");
        user.setEmail("john@example.com");
        user.setPasswordHash("hashed-password");
        user.setStatus(UserStatus.ACTIVE);

        // Save user
        userRepository.save(user);

        // Find user by email
        Optional<User> foundUser =
                userRepository.findByEmail("john@example.com");

        // Verify result
        assertThat(foundUser).isPresent();
        assertThat(foundUser.get().getName()).isEqualTo("John Smith");
    }

    @Test
    void shouldReturnTrueWhenEmailAlreadyExists() {

        User user = new User();
        user.setName("Jane Smith");
        user.setEmail("jane@example.com");
        user.setPasswordHash("hashed-password");

        userRepository.save(user);

        boolean exists = userRepository.existsByEmail("jane@example.com");

        assertThat(exists).isTrue();
    }
}
