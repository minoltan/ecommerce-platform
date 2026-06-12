package com.ecommerce.userauth.api.v1;

import com.ecommerce.userauth.AbstractIntegrationTest;
import com.ecommerce.userauth.api.v1.dto.DeactivateUserResponse;
import com.ecommerce.userauth.api.v1.dto.ErrorResponse;
import com.ecommerce.userauth.api.v1.dto.LoginRequest;
import com.ecommerce.userauth.api.v1.dto.RegisterRequest;
import com.ecommerce.userauth.api.v1.dto.RegisterResponse;
import com.ecommerce.userauth.api.v1.dto.TokenResponse;
import com.ecommerce.userauth.api.v1.dto.UserListResponse;
import com.ecommerce.userauth.api.v1.dto.VerifyEmailRequest;
import com.ecommerce.userauth.domain.User;
import com.ecommerce.userauth.domain.UserRole;
import com.ecommerce.userauth.domain.UserStatus;
import com.ecommerce.userauth.repository.EmailVerificationRepository;
import com.ecommerce.userauth.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class AdminUserControllerIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private EmailVerificationRepository emailVerificationRepository;

    @Autowired
    private UserRepository userRepository;

    @Test
    void adminCanListAndDeactivateAUser() {
        String adminToken = registerVerifyAndPromote("admin", UserRole.ADMIN);
        UUID targetUserId = registerAndVerify("target");

        ResponseEntity<UserListResponse> listResponse = restTemplate.exchange(
                "/v1/admin/users", HttpMethod.GET, bearer(adminToken), UserListResponse.class);
        assertThat(listResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(listResponse.getBody().users())
                .anySatisfy(u -> assertThat(u.userId()).isEqualTo(targetUserId));

        ResponseEntity<DeactivateUserResponse> deactivateResponse = restTemplate.exchange(
                "/v1/admin/users/" + targetUserId + "/deactivate", HttpMethod.POST, bearer(adminToken),
                DeactivateUserResponse.class);
        assertThat(deactivateResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(deactivateResponse.getBody().status()).isEqualTo(UserStatus.DEACTIVATED);

        User deactivated = userRepository.findById(targetUserId).orElseThrow();
        assertThat(deactivated.getStatus()).isEqualTo(UserStatus.DEACTIVATED);
        assertThat(deactivated.getDeactivatedAt()).isPresent();
    }

    @Test
    void nonAdminIsForbiddenFromAdminEndpoints() {
        String customerToken = registerVerifyAndPromote("customer", UserRole.CUSTOMER);

        ResponseEntity<ErrorResponse> response = restTemplate.exchange(
                "/v1/admin/users", HttpMethod.GET, bearer(customerToken), ErrorResponse.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    void deactivatingUnknownUserReturnsNotFound() {
        String adminToken = registerVerifyAndPromote("admin2", UserRole.ADMIN);

        ResponseEntity<ErrorResponse> response = restTemplate.exchange(
                "/v1/admin/users/" + UUID.randomUUID() + "/deactivate", HttpMethod.POST, bearer(adminToken),
                ErrorResponse.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(response.getBody().code()).isEqualTo("USER_NOT_FOUND");
    }

    @Test
    void adminEndpointsRejectRequestsWithoutAToken() {
        ResponseEntity<ErrorResponse> response = restTemplate.exchange(
                "/v1/admin/users", HttpMethod.GET, HttpEntity.EMPTY, ErrorResponse.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    private UUID registerAndVerify(String namePrefix) {
        String email = namePrefix + "+" + System.nanoTime() + "@example.com";
        ResponseEntity<RegisterResponse> registerResponse = restTemplate.postForEntity(
                "/v1/auth/register", new RegisterRequest(email, "password123", namePrefix), RegisterResponse.class);
        UUID userId = registerResponse.getBody().userId();

        String verificationToken = emailVerificationRepository.findAll().stream()
                .filter(v -> v.getUserId().equals(userId))
                .findFirst().orElseThrow().getToken();
        restTemplate.postForEntity("/v1/auth/verify-email", new VerifyEmailRequest(verificationToken), TokenResponse.class);

        return userId;
    }

    /** Registers and verifies a user, promotes it to {@code role}, then logs in for a fresh access token carrying that role. */
    private String registerVerifyAndPromote(String namePrefix, UserRole role) {
        String email = namePrefix + "+" + System.nanoTime() + "@example.com";
        UUID userId = registerAndVerify(namePrefix);

        User user = userRepository.findById(userId).orElseThrow();
        ReflectionTestUtils.setField(user, "role", role);
        userRepository.save(user);

        ResponseEntity<TokenResponse> loginResponse = restTemplate.postForEntity(
                "/v1/auth/login", new LoginRequest(email, "password123"), TokenResponse.class);
        return loginResponse.getBody().accessToken();
    }

    private HttpEntity<Void> bearer(String accessToken) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(accessToken);
        return new HttpEntity<>(headers);
    }
}
