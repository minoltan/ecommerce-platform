package com.ecommerce.userauth.api.v1;

import com.ecommerce.userauth.api.v1.dto.ErrorResponse;
import com.ecommerce.userauth.api.v1.dto.ValidationErrorResponse;
import com.ecommerce.userauth.domain.EmailAlreadyRegisteredException;
import com.ecommerce.userauth.domain.IllegalStateTransitionException;
import com.ecommerce.userauth.domain.InvalidAccessTokenException;
import com.ecommerce.userauth.domain.InvalidCredentialsException;
import com.ecommerce.userauth.domain.InvalidRefreshTokenException;
import com.ecommerce.userauth.domain.InvalidTokenException;
import com.ecommerce.userauth.domain.RateLimitExceededException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.List;
import java.util.UUID;

/** Maps domain/validation exceptions to the {@code ErrorResponse}/{@code ValidationErrorResponse} schemas. */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(EmailAlreadyRegisteredException.class)
    public ResponseEntity<ErrorResponse> handleEmailAlreadyRegistered(EmailAlreadyRegisteredException e) {
        return error(HttpStatus.CONFLICT, "EMAIL_ALREADY_REGISTERED", e.getMessage());
    }

    @ExceptionHandler(InvalidCredentialsException.class)
    public ResponseEntity<ErrorResponse> handleInvalidCredentials(InvalidCredentialsException e) {
        return error(HttpStatus.UNAUTHORIZED, "INVALID_CREDENTIALS", e.getMessage());
    }

    @ExceptionHandler(IllegalStateTransitionException.class)
    public ResponseEntity<ErrorResponse> handleIllegalStateTransition(IllegalStateTransitionException e) {
        return error(HttpStatus.FORBIDDEN, "ACCOUNT_NOT_ACTIVE", e.getMessage());
    }

    @ExceptionHandler(InvalidTokenException.class)
    public ResponseEntity<ErrorResponse> handleInvalidToken(InvalidTokenException e) {
        return error(HttpStatus.BAD_REQUEST, "INVALID_TOKEN", e.getMessage());
    }

    @ExceptionHandler(InvalidRefreshTokenException.class)
    public ResponseEntity<ErrorResponse> handleInvalidRefreshToken(InvalidRefreshTokenException e) {
        return error(HttpStatus.UNAUTHORIZED, "INVALID_REFRESH_TOKEN", e.getMessage());
    }

    @ExceptionHandler(InvalidAccessTokenException.class)
    public ResponseEntity<ErrorResponse> handleInvalidAccessToken(InvalidAccessTokenException e) {
        return error(HttpStatus.UNAUTHORIZED, "INVALID_ACCESS_TOKEN", e.getMessage());
    }

    @ExceptionHandler(RateLimitExceededException.class)
    public ResponseEntity<ErrorResponse> handleRateLimitExceeded(RateLimitExceededException e) {
        return error(HttpStatus.TOO_MANY_REQUESTS, "RATE_LIMIT_EXCEEDED", e.getMessage());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ValidationErrorResponse> handleValidation(MethodArgumentNotValidException e) {
        List<ValidationErrorResponse.FieldError> fieldErrors = e.getBindingResult().getFieldErrors().stream()
                .map(fe -> new ValidationErrorResponse.FieldError(fe.getField(), fe.getDefaultMessage()))
                .toList();
        ValidationErrorResponse body = new ValidationErrorResponse(
                "VALIDATION_FAILED", "Request validation failed", UUID.randomUUID(), fieldErrors);
        return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY).body(body);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleUnexpected(Exception e) {
        UUID correlationId = UUID.randomUUID();
        log.error("Unhandled exception [correlationId={}]", correlationId, e);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(new ErrorResponse("INTERNAL_ERROR", "An unexpected error occurred", correlationId));
    }

    private ResponseEntity<ErrorResponse> error(HttpStatus status, String code, String message) {
        return ResponseEntity.status(status).body(new ErrorResponse(code, message, UUID.randomUUID()));
    }
}
