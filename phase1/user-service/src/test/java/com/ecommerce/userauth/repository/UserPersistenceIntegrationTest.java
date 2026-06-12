package com.ecommerce.userauth.repository;

import com.ecommerce.userauth.AbstractIntegrationTest;
import com.ecommerce.userauth.domain.Email;
import com.ecommerce.userauth.domain.User;
import com.ecommerce.userauth.domain.UserAddress;
import com.ecommerce.userauth.domain.UserStatus;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class UserPersistenceIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private UserRepository userRepository;

    @Test
    void savesAndReloadsUserWithAddresses() {
        User user = User.register(new Email("persist@example.com"), "correct-horse-battery", "Persist Test");
        user.addAddress(new UserAddress("1 Main St", null, "Chennai", "TN", "600001", "IN"));
        userRepository.save(user);

        Optional<User> reloaded = userRepository.findByEmail(new Email("persist@example.com"));

        assertThat(reloaded).isPresent();
        assertThat(reloaded.get().getStatus()).isEqualTo(UserStatus.UNVERIFIED);
        assertThat(reloaded.get().getAddresses()).hasSize(1);
    }

    @Test
    void enforcesUniqueEmailConstraint() {
        userRepository.save(
                User.register(new Email("dup@example.com"), "correct-horse-battery", "First"));
        userRepository.flush();

        assertThatThrownBy(() -> {
            userRepository.save(
                    User.register(new Email("dup@example.com"), "correct-horse-battery", "Second"));
            userRepository.flush();
        }).isInstanceOf(DataIntegrityViolationException.class);
    }
}
