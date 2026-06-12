package com.ecommerce.userauth.repository;

import java.util.UUID;

/** Result of {@link RefreshTokenRepository#rotate}: the new token plus the session's owner. */
public record RotatedRefreshToken(UUID userId, IssuedRefreshToken token) {
}
