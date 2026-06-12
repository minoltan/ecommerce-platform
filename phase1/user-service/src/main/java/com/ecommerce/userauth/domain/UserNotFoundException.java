package com.ecommerce.userauth.domain;

import java.util.UUID;

/** Thrown when an admin operation targets a {@link User} id that does not exist. */
public class UserNotFoundException extends RuntimeException {

    public UserNotFoundException(UUID userId) {
        super("User not found: " + userId);
    }
}
