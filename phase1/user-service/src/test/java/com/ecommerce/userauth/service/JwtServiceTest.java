package com.ecommerce.userauth.service;

import com.ecommerce.userauth.domain.Email;
import com.ecommerce.userauth.domain.User;
import com.ecommerce.userauth.domain.UserRole;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jws;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.security.KeyPairGenerator;
import java.security.NoSuchAlgorithmException;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class JwtServiceTest {

    private static final long TTL_SECONDS = 900;

    private JwtService jwtService;
    private User user;

    @BeforeEach
    void setUp() throws NoSuchAlgorithmException {
        KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(2048);
        jwtService = new JwtService(generator.generateKeyPair(), "ecommerce-platform", TTL_SECONDS);
        user = User.register(new Email("jane.doe@example.com"), "password123", "Jane Doe");
    }

    @Test
    void issuesAccessTokenWithExpectedClaims() {
        IssuedAccessToken issued = jwtService.issueAccessToken(user);

        Jws<Claims> jws = jwtService.parse(issued.token());
        Claims claims = jws.getPayload();

        assertThat(claims.getSubject()).isEqualTo(user.getId().toString());
        assertThat(claims.get("email")).isEqualTo("jane.doe@example.com");
        assertThat(claims.get("role")).isEqualTo(UserRole.CUSTOMER.name());
        assertThat(claims.getId()).isEqualTo(issued.jti());
        assertThat(claims.getIssuer()).isEqualTo("ecommerce-platform");
        assertThat(claims.getExpiration().toInstant()).isEqualTo(issued.expiresAt().truncatedTo(java.time.temporal.ChronoUnit.SECONDS));
        assertThat(issued.expiresInSeconds()).isEqualTo(TTL_SECONDS);
    }

    @Test
    @SuppressWarnings("unchecked")
    void exposesPublicKeyViaJwks() {
        Map<String, Object> jwks = jwtService.jwks();

        List<Map<String, Object>> keys = (List<Map<String, Object>>) jwks.get("keys");
        assertThat(keys).hasSize(1);

        Map<String, Object> key = keys.get(0);
        assertThat(key.get("kty")).isEqualTo("RSA");
        assertThat(key.get("alg")).isEqualTo("RS256");
        assertThat(key.get("use")).isEqualTo("sig");
        assertThat(key).containsKeys("kid", "n", "e");
    }
}
