package com.ecommerce.userauth.api.v1.dto;

import com.ecommerce.userauth.repository.IssuedRefreshToken;
import com.ecommerce.userauth.service.IssuedTokens;

public record TokenResponse(String accessToken, String refreshToken, long expiresIn, String tokenType) {

    public static TokenResponse from(IssuedTokens tokens) {
        IssuedRefreshToken refreshToken = tokens.refreshToken();
        return new TokenResponse(
                tokens.accessToken().token(),
                refreshToken.token(),
                tokens.accessToken().expiresInSeconds(),
                "Bearer");
    }
}
