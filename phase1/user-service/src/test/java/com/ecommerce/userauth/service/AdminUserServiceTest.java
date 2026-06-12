package com.ecommerce.userauth.service;

import com.ecommerce.userauth.domain.Email;
import com.ecommerce.userauth.domain.IllegalStateTransitionException;
import com.ecommerce.userauth.domain.User;
import com.ecommerce.userauth.domain.UserNotFoundException;
import com.ecommerce.userauth.domain.UserRole;
import com.ecommerce.userauth.domain.UserStatus;
import com.ecommerce.userauth.repository.OutboxEventRepository;
import com.ecommerce.userauth.repository.RefreshTokenRepository;
import com.ecommerce.userauth.repository.UserRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AdminUserServiceTest {

    @Mock
    private UserRepository userRepository;
    @Mock
    private RefreshTokenRepository refreshTokenRepository;
    @Mock
    private OutboxEventRepository outboxEventRepository;

    private AdminUserService adminUserService;

    private static final Email EMAIL = new Email("jane.doe@example.com");
    private static final String PASSWORD = "password123";

    @BeforeEach
    void setUp() {
        adminUserService = new AdminUserService(userRepository, refreshTokenRepository, outboxEventRepository,
                new ObjectMapper().registerModule(new JavaTimeModule()));
    }

    @Test
    void listUsersWithNoFiltersReturnsAllUsers() {
        Pageable pageable = PageRequest.of(0, 20);
        User user = User.register(EMAIL, PASSWORD, "Jane Doe");
        when(userRepository.findAll(pageable)).thenReturn(new PageImpl<>(List.of(user)));

        Page<User> result = adminUserService.listUsers(null, null, pageable);

        assertThat(result.getContent()).containsExactly(user);
    }

    @Test
    void listUsersFiltersByStatusAndRole() {
        Pageable pageable = PageRequest.of(0, 20);
        User user = User.register(EMAIL, PASSWORD, "Jane Doe");
        when(userRepository.findByStatusAndRole(UserStatus.ACTIVE, UserRole.ADMIN, pageable))
                .thenReturn(new PageImpl<>(List.of(user)));

        Page<User> result = adminUserService.listUsers(UserStatus.ACTIVE, UserRole.ADMIN, pageable);

        assertThat(result.getContent()).containsExactly(user);
        verify(userRepository, never()).findAll(any(Pageable.class));
    }

    @Test
    void listUsersFiltersByStatusOnly() {
        Pageable pageable = PageRequest.of(0, 20);
        User user = User.register(EMAIL, PASSWORD, "Jane Doe");
        when(userRepository.findByStatus(UserStatus.UNVERIFIED, pageable))
                .thenReturn(new PageImpl<>(List.of(user)));

        Page<User> result = adminUserService.listUsers(UserStatus.UNVERIFIED, null, pageable);

        assertThat(result.getContent()).containsExactly(user);
    }

    @Test
    void listUsersFiltersByRoleOnly() {
        Pageable pageable = PageRequest.of(0, 20);
        User user = User.register(EMAIL, PASSWORD, "Jane Doe");
        when(userRepository.findByRole(UserRole.ADMIN, pageable))
                .thenReturn(new PageImpl<>(List.of(user)));

        Page<User> result = adminUserService.listUsers(null, UserRole.ADMIN, pageable);

        assertThat(result.getContent()).containsExactly(user);
        verify(userRepository, never()).findAll(any(Pageable.class));
    }

    @Test
    void deactivateUserTransitionsStatusRevokesSessionsAndWritesOutboxEvent() {
        User user = User.register(EMAIL, PASSWORD, "Jane Doe");
        when(userRepository.findById(user.getId())).thenReturn(Optional.of(user));

        User result = adminUserService.deactivateUser(user.getId());

        assertThat(result.getStatus()).isEqualTo(UserStatus.DEACTIVATED);
        verify(userRepository).save(user);
        verify(refreshTokenRepository).revokeAll(user.getId());
        verify(outboxEventRepository).save(any());
    }

    @Test
    void deactivateUnknownUserThrowsUserNotFound() {
        UUID userId = UUID.randomUUID();
        when(userRepository.findById(userId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> adminUserService.deactivateUser(userId))
                .isInstanceOf(UserNotFoundException.class);

        verify(refreshTokenRepository, never()).revokeAll(any());
        verify(outboxEventRepository, never()).save(any());
    }

    @Test
    void deactivateAlreadyDeactivatedUserThrowsIllegalStateTransition() {
        User user = User.register(EMAIL, PASSWORD, "Jane Doe");
        user.deactivate();
        when(userRepository.findById(user.getId())).thenReturn(Optional.of(user));

        assertThatThrownBy(() -> adminUserService.deactivateUser(user.getId()))
                .isInstanceOf(IllegalStateTransitionException.class);

        verify(userRepository, never()).save(any());
        verify(refreshTokenRepository, never()).revokeAll(any());
        verify(outboxEventRepository, never()).save(any());
    }

    @Test
    void deactivateUserPropagatesExceptionWhenOutboxWriteFails() {
        User user = User.register(EMAIL, PASSWORD, "Jane Doe");
        when(userRepository.findById(user.getId())).thenReturn(Optional.of(user));
        when(outboxEventRepository.save(any())).thenThrow(new RuntimeException("db unavailable"));

        assertThatThrownBy(() -> adminUserService.deactivateUser(user.getId()))
                .isInstanceOf(RuntimeException.class)
                .hasMessage("db unavailable");

        // @Transactional ensures the surrounding transaction (including userRepository.save)
        // rolls back when this exception propagates out of the service method.
        assertThat(user.getStatus()).isEqualTo(UserStatus.DEACTIVATED);
    }
}
