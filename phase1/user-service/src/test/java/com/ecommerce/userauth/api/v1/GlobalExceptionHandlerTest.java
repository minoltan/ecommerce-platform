package com.ecommerce.userauth.api.v1;

import com.ecommerce.userauth.domain.Email;
import com.ecommerce.userauth.domain.EmailAlreadyRegisteredException;
import com.ecommerce.userauth.domain.IllegalStateTransitionException;
import com.ecommerce.userauth.domain.InvalidAccessTokenException;
import com.ecommerce.userauth.domain.InvalidCredentialsException;
import com.ecommerce.userauth.domain.InvalidRefreshTokenException;
import com.ecommerce.userauth.domain.InvalidTokenException;
import com.ecommerce.userauth.domain.RateLimitExceededException;
import com.ecommerce.userauth.domain.UserNotFoundException;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class GlobalExceptionHandlerTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    @Test
    void mapsEmailAlreadyRegisteredToConflict() {
        ResponseEntity<?> response = handler.handleEmailAlreadyRegistered(new EmailAlreadyRegisteredException(new Email("dup@example.com")));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(code(response)).isEqualTo("EMAIL_ALREADY_REGISTERED");
    }

    @Test
    void mapsInvalidCredentialsToUnauthorized() {
        ResponseEntity<?> response = handler.handleInvalidCredentials(new InvalidCredentialsException());

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(code(response)).isEqualTo("INVALID_CREDENTIALS");
    }

    @Test
    void mapsIllegalStateTransitionToForbidden() {
        ResponseEntity<?> response = handler.handleIllegalStateTransition(new IllegalStateTransitionException("not active"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(code(response)).isEqualTo("ACCOUNT_NOT_ACTIVE");
    }

    @Test
    void mapsInvalidTokenToBadRequest() {
        ResponseEntity<?> response = handler.handleInvalidToken(new InvalidTokenException("bad token"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(code(response)).isEqualTo("INVALID_TOKEN");
    }

    @Test
    void mapsInvalidRefreshTokenToUnauthorized() {
        ResponseEntity<?> response = handler.handleInvalidRefreshToken(new InvalidRefreshTokenException());

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(code(response)).isEqualTo("INVALID_REFRESH_TOKEN");
    }

    @Test
    void mapsInvalidAccessTokenToUnauthorized() {
        ResponseEntity<?> response = handler.handleInvalidAccessToken(new InvalidAccessTokenException());

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(code(response)).isEqualTo("INVALID_ACCESS_TOKEN");
    }

    @Test
    void mapsRateLimitExceededToTooManyRequests() {
        ResponseEntity<?> response = handler.handleRateLimitExceeded(new RateLimitExceededException("slow down"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS);
        assertThat(code(response)).isEqualTo("RATE_LIMIT_EXCEEDED");
    }

    @Test
    void mapsUserNotFoundToNotFound() {
        ResponseEntity<?> response = handler.handleUserNotFound(new UserNotFoundException(UUID.randomUUID()));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(code(response)).isEqualTo("USER_NOT_FOUND");
    }

    @Test
    void mapsUnexpectedExceptionToInternalErrorWithoutLeakingDetails() {
        ResponseEntity<?> response = handler.handleUnexpected(new RuntimeException("boom: connection string=secret"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
        assertThat(code(response)).isEqualTo("INTERNAL_ERROR");
    }

    private String code(ResponseEntity<?> response) {
        return ((com.ecommerce.userauth.api.v1.dto.ErrorResponse) response.getBody()).code();
    }
}
