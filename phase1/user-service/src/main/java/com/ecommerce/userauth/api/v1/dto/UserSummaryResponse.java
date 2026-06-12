package com.ecommerce.userauth.api.v1.dto;

import com.ecommerce.userauth.domain.User;
import com.ecommerce.userauth.domain.UserRole;
import com.ecommerce.userauth.domain.UserStatus;

import java.time.Instant;
import java.util.UUID;

public record UserSummaryResponse(UUID userId, String email, String displayName, UserStatus status, UserRole role,
                                    Instant createdAt) {

    public static UserSummaryResponse from(User user) {
        return new UserSummaryResponse(user.getId(), user.getEmail().value(), user.getFullName(),
                user.getStatus(), user.getRole(), user.getCreatedAt());
    }
}
