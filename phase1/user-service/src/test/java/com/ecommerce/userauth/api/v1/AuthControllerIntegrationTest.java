package com.ecommerce.userauth.api.v1;

import com.ecommerce.userauth.AbstractIntegrationTest;
import com.ecommerce.userauth.api.v1.dto.ErrorResponse;
import com.ecommerce.userauth.api.v1.dto.LoginRequest;
import com.ecommerce.userauth.api.v1.dto.RefreshTokenRequest;
import com.ecommerce.userauth.api.v1.dto.RegisterRequest;
import com.ecommerce.userauth.api.v1.dto.RegisterResponse;
import com.ecommerce.userauth.api.v1.dto.TokenResponse;
import com.ecommerce.userauth.api.v1.dto.VerifyEmailRequest;
import com.ecommerce.userauth.repository.EmailVerificationRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import static org.assertj.core.api.Assertions.assertThat;

class AuthControllerIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private EmailVerificationRepository emailVerificationRepository;

    @Test
    void registerVerifyLoginRefreshLogoutHappyPath() {
        String email = "alice+" + System.nanoTime() + "@example.com";

        ResponseEntity<RegisterResponse> registerResponse = restTemplate.postForEntity(
                "/v1/auth/register", new RegisterRequest(email, "password123", "Alice"), RegisterResponse.class);
        assertThat(registerResponse.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(registerResponse.getBody().userId()).isNotNull();

        String verificationToken = emailVerificationRepository.findAll().stream()
                .filter(v -> v.getUserId().equals(registerResponse.getBody().userId()))
                .findFirst().orElseThrow().getToken();

        ResponseEntity<TokenResponse> verifyResponse = restTemplate.postForEntity(
                "/v1/auth/verify-email", new VerifyEmailRequest(verificationToken), TokenResponse.class);
        assertThat(verifyResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(verifyResponse.getBody().accessToken()).isNotBlank();

        ResponseEntity<TokenResponse> loginResponse = restTemplate.postForEntity(
                "/v1/auth/login", new LoginRequest(email, "password123"), TokenResponse.class);
        assertThat(loginResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        TokenResponse loginTokens = loginResponse.getBody();
        assertThat(loginTokens.accessToken()).isNotBlank();
        assertThat(loginTokens.refreshToken()).isNotBlank();
        assertThat(loginTokens.tokenType()).isEqualTo("Bearer");

        ResponseEntity<TokenResponse> refreshResponse = restTemplate.postForEntity(
                "/v1/auth/refresh", new RefreshTokenRequest(loginTokens.refreshToken()), TokenResponse.class);
        assertThat(refreshResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        TokenResponse refreshedTokens = refreshResponse.getBody();
        assertThat(refreshedTokens.accessToken()).isNotBlank();
        assertThat(refreshedTokens.refreshToken()).isNotEqualTo(loginTokens.refreshToken());

        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(refreshedTokens.accessToken());
        HttpEntity<RefreshTokenRequest> logoutRequest =
                new HttpEntity<>(new RefreshTokenRequest(refreshedTokens.refreshToken()), headers);
        ResponseEntity<Void> logoutResponse = restTemplate.exchange(
                "/v1/auth/logout", HttpMethod.POST, logoutRequest, Void.class);
        assertThat(logoutResponse.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);

        ResponseEntity<ErrorResponse> reuseRefreshResponse = restTemplate.postForEntity(
                "/v1/auth/refresh", new RefreshTokenRequest(refreshedTokens.refreshToken()), ErrorResponse.class);
        assertThat(reuseRefreshResponse.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(reuseRefreshResponse.getBody().code()).isEqualTo("INVALID_REFRESH_TOKEN");
    }

    @Test
    void registerRejectsDuplicateEmailWithConflict() {
        String email = "bob+" + System.nanoTime() + "@example.com";
        restTemplate.postForEntity("/v1/auth/register", new RegisterRequest(email, "password123", "Bob"), RegisterResponse.class);

        ResponseEntity<ErrorResponse> duplicateResponse = restTemplate.postForEntity(
                "/v1/auth/register", new RegisterRequest(email, "password123", "Bob"), ErrorResponse.class);

        assertThat(duplicateResponse.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(duplicateResponse.getBody().code()).isEqualTo("EMAIL_ALREADY_REGISTERED");
    }

    @Test
    void registerRejectsInvalidPayloadWithUnprocessableEntity() {
        ResponseEntity<String> response = restTemplate.postForEntity(
                "/v1/auth/register", new RegisterRequest("not-an-email", "short", ""), String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
    }

    @Test
    void loginWithUnknownEmailReturnsUnauthorized() {
        ResponseEntity<ErrorResponse> response = restTemplate.postForEntity(
                "/v1/auth/login", new LoginRequest("nobody@example.com", "password123"), ErrorResponse.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(response.getBody().code()).isEqualTo("INVALID_CREDENTIALS");
    }

    @Test
    void loginBeforeEmailVerificationReturnsForbidden() {
        String email = "carol+" + System.nanoTime() + "@example.com";
        restTemplate.postForEntity("/v1/auth/register", new RegisterRequest(email, "password123", "Carol"), RegisterResponse.class);

        ResponseEntity<ErrorResponse> response = restTemplate.postForEntity(
                "/v1/auth/login", new LoginRequest(email, "password123"), ErrorResponse.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(response.getBody().code()).isEqualTo("ACCOUNT_NOT_ACTIVE");
    }

    @Test
    void refreshWithMalformedTokenReturnsUnauthorized() {
        ResponseEntity<ErrorResponse> response = restTemplate.postForEntity(
                "/v1/auth/refresh", new RefreshTokenRequest("not-a-real-token"), ErrorResponse.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(response.getBody().code()).isEqualTo("INVALID_REFRESH_TOKEN");
    }

    @Test
    void verifyEmailWithUnknownTokenReturnsBadRequest() {
        ResponseEntity<ErrorResponse> response = restTemplate.postForEntity(
                "/v1/auth/verify-email", new VerifyEmailRequest("unknown-token"), ErrorResponse.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody().code()).isEqualTo("INVALID_TOKEN");
    }

    @Test
    void logoutWithoutBearerTokenReturnsUnauthorized() {
        ResponseEntity<ErrorResponse> response = restTemplate.postForEntity(
                "/v1/auth/logout", new RefreshTokenRequest("00000000-0000-0000-0000-000000000000.id.secret"), ErrorResponse.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(response.getBody().code()).isEqualTo("INVALID_ACCESS_TOKEN");
    }
}
