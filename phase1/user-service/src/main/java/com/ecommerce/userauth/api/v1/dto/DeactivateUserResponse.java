package com.ecommerce.userauth.api.v1.dto;

import com.ecommerce.userauth.domain.User;
import com.ecommerce.userauth.domain.UserStatus;

import java.util.UUID;

public record DeactivateUserResponse(UUID userId, UserStatus status) {

    public static DeactivateUserResponse from(User user) {
        return new DeactivateUserResponse(user.getId(), user.getStatus());
    }
}
