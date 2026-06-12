package com.ecommerce.userauth.domain;

/** Thrown when an email-verification or password-reset token is unknown, used, or expired. */
public class InvalidTokenException extends RuntimeException {

    public InvalidTokenException(String message) {
        super(message);
    }
}
