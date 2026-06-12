package com.ecommerce.userauth.domain;

/** Thrown when an endpoint requiring a bearer access token receives none, or an invalid/expired one. */
public class InvalidAccessTokenException extends RuntimeException {

    public InvalidAccessTokenException() {
        super("Access token is missing, invalid, or expired");
    }
}
