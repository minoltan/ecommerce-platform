package com.ecommerce.userauth.api.v1;

import com.ecommerce.userauth.api.v1.dto.LoginRequest;
import com.ecommerce.userauth.api.v1.dto.RefreshTokenRequest;
import com.ecommerce.userauth.api.v1.dto.RegisterRequest;
import com.ecommerce.userauth.api.v1.dto.RegisterResponse;
import com.ecommerce.userauth.api.v1.dto.TokenResponse;
import com.ecommerce.userauth.api.v1.dto.VerifyEmailRequest;
import com.ecommerce.userauth.domain.Email;
import com.ecommerce.userauth.domain.InvalidAccessTokenException;
import com.ecommerce.userauth.service.AuthService;
import com.ecommerce.userauth.service.JwtService;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

/** Auth API per docs/api-specs/user-service-api.yaml — register, verify-email, login, refresh, logout. */
@RestController
@RequestMapping("/v1/auth")
public class AuthController {

    private static final String BEARER_PREFIX = "Bearer ";

    private final AuthService authService;
    private final JwtService jwtService;

    public AuthController(AuthService authService, JwtService jwtService) {
        this.authService = authService;
        this.jwtService = jwtService;
    }

    @PostMapping("/register")
    @ResponseStatus(HttpStatus.CREATED)
    public RegisterResponse register(@Valid @RequestBody RegisterRequest request, HttpServletRequest httpRequest) {
        UUID userId = authService.register(
                new Email(request.email()), request.password(), request.displayName(), clientIp(httpRequest));
        return new RegisterResponse(userId);
    }

    @PostMapping("/verify-email")
    public TokenResponse verifyEmail(@Valid @RequestBody VerifyEmailRequest request) {
        return TokenResponse.from(authService.verifyEmail(request.token()));
    }

    @PostMapping("/login")
    public TokenResponse login(@Valid @RequestBody LoginRequest request) {
        return TokenResponse.from(authService.login(new Email(request.email()), request.password()));
    }

    @PostMapping("/refresh")
    public TokenResponse refresh(@Valid @RequestBody RefreshTokenRequest request) {
        return TokenResponse.from(authService.refresh(request.refreshToken()));
    }

    @PostMapping("/logout")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void logout(@Valid @RequestBody RefreshTokenRequest request,
                        @RequestHeader(value = "Authorization", required = false) String authorizationHeader) {
        if (authorizationHeader == null || !authorizationHeader.startsWith(BEARER_PREFIX)) {
            throw new InvalidAccessTokenException();
        }

        Claims claims;
        try {
            claims = jwtService.parse(authorizationHeader.substring(BEARER_PREFIX.length())).getPayload();
        } catch (JwtException e) {
            throw new InvalidAccessTokenException();
        }

        Duration remainingTtl = Duration.between(Instant.now(), claims.getExpiration().toInstant());
        authService.logout(request.refreshToken(), claims.getId(), remainingTtl);
    }

    private String clientIp(HttpServletRequest request) {
        String forwardedFor = request.getHeader("X-Forwarded-For");
        if (forwardedFor != null && !forwardedFor.isBlank()) {
            return forwardedFor.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }
}
