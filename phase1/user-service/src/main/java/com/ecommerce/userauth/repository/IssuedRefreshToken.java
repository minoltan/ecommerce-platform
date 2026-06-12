package com.ecommerce.userauth.repository;

/**
 * A freshly-issued opaque refresh token returned to the client, paired with the
 * {@code tokenId} used to address its Redis entry ({@code refresh:{userId}:{tokenId}}, LLD §6.1).
 */
public record IssuedRefreshToken(String token, String tokenId) {
}
