package com.ecommerce.userauth.domain;

/** Thrown when a presented refresh token is malformed, unknown, expired, or already rotated/revoked. */
public class InvalidRefreshTokenException extends RuntimeException {

    public InvalidRefreshTokenException() {
        super("Refresh token is invalid or expired");
    }
}
