package com.ecommerce.userauth.domain;

import java.util.Objects;
import java.util.regex.Pattern;

/**
 * Value object per docs/lld/user-auth-lld.md §3.3 — RFC 5322-ish format validation,
 * lowercased on construction, immutable after {@link User} creation.
 */
public record Email(String value) {

    private static final Pattern FORMAT = Pattern.compile(
            "^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$");

    public Email {
        Objects.requireNonNull(value, "email must not be null");
        value = value.trim().toLowerCase();
        if (!FORMAT.matcher(value).matches()) {
            throw new IllegalArgumentException("Invalid email format: " + value);
        }
    }
}
