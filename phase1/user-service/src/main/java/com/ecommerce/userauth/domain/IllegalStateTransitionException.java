package com.ecommerce.userauth.domain;

/**
 * Thrown when a {@link User} command is attempted while the aggregate is in a status
 * that does not permit it, per docs/lld/user-auth-lld.md §5.
 */
public class IllegalStateTransitionException extends RuntimeException {

    public IllegalStateTransitionException(String message) {
        super(message);
    }
}
