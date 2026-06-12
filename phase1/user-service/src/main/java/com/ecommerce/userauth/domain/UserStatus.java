package com.ecommerce.userauth.domain;

/**
 * {@code User.status} per docs/lld/user-auth-lld.md §5 (state machine).
 */
public enum UserStatus {
    UNVERIFIED,
    ACTIVE,
    DEACTIVATED
}
