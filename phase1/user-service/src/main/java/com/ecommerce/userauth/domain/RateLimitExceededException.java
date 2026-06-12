package com.ecommerce.userauth.domain;

/** Thrown when a rate limit from LLD §6.4 (e.g. {@code rate:{userId}:login}) is exceeded. */
public class RateLimitExceededException extends RuntimeException {

    public RateLimitExceededException(String message) {
        super(message);
    }
}
