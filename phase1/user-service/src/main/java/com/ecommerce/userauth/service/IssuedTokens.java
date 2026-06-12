package com.ecommerce.userauth.service;

import com.ecommerce.userauth.repository.IssuedRefreshToken;

/** Result of an authentication flow that issues a fresh token pair (login, refresh, verify-email). */
public record IssuedTokens(IssuedAccessToken accessToken, IssuedRefreshToken refreshToken) {
}
