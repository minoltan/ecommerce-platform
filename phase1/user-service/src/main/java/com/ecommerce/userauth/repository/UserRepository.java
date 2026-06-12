package com.ecommerce.userauth.repository;

import com.ecommerce.userauth.domain.Email;
import com.ecommerce.userauth.domain.User;
import com.ecommerce.userauth.domain.UserRole;
import com.ecommerce.userauth.domain.UserStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface UserRepository extends JpaRepository<User, UUID> {

    Optional<User> findByEmail(Email email);

    boolean existsByEmail(Email email);

    /** Admin search per docs/lld/user-auth-lld.md §7 (uses idx_users_status). */
    Page<User> findByStatus(UserStatus status, Pageable pageable);

    Page<User> findByRole(UserRole role, Pageable pageable);

    Page<User> findByStatusAndRole(UserStatus status, UserRole role, Pageable pageable);
}
