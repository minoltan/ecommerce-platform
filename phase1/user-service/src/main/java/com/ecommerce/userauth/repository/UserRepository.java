package com.ecommerce.userauth.repository;

import com.ecommerce.userauth.domain.Email;
import com.ecommerce.userauth.domain.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface UserRepository extends JpaRepository<User, UUID> {

    Optional<User> findByEmail(Email email);

    boolean existsByEmail(Email email);
}
