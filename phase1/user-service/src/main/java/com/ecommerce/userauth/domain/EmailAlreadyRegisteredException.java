package com.ecommerce.userauth.domain;

/** Thrown by {@code AuthService.register} when the email is already associated with an account. */
public class EmailAlreadyRegisteredException extends RuntimeException {

    public EmailAlreadyRegisteredException(Email email) {
        super("Email is already registered: " + email.value());
    }
}
