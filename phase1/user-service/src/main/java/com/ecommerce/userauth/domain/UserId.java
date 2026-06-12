package com.ecommerce.userauth.domain;

import java.util.UUID;

/**
 * Value object per docs/lld/user-auth-lld.md §3.3 — UUID v4, assigned on creation.
 */
public record UserId(UUID value) {

    public static UserId newId() {
        return new UserId(UUID.randomUUID());
    }
}
