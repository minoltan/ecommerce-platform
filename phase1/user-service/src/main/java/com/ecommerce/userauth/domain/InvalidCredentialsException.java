package com.ecommerce.userauth.domain;

/**
 * Thrown by {@link User#login(String)} and {@link User#changePassword(String, String)}
 * when the supplied password does not match the stored {@link PasswordHash}.
 */
public class InvalidCredentialsException extends RuntimeException {

    public InvalidCredentialsException() {
        super("Invalid credentials");
    }
}
