package com.ecommerce.userauth.service;

import com.ecommerce.userauth.domain.User;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jws;
import io.jsonwebtoken.Jwts;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.security.KeyPair;
import java.security.interfaces.RSAPublicKey;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Issues and verifies the RS256 JWT access tokens described in ADR-0011, with claims
 * {@code {sub, email, role, jti, iat, exp}}, and publishes the corresponding JWKS.
 */
@Service
public class JwtService {

    private static final String KEY_ID = "user-auth-1";

    private final KeyPair keyPair;
    private final String issuer;
    private final Duration accessTokenTtl;

    public JwtService(KeyPair jwtKeyPair,
                       @Value("${jwt.issuer}") String issuer,
                       @Value("${jwt.access-token-ttl-seconds}") long accessTokenTtlSeconds) {
        this.keyPair = jwtKeyPair;
        this.issuer = issuer;
        this.accessTokenTtl = Duration.ofSeconds(accessTokenTtlSeconds);
    }

    public IssuedAccessToken issueAccessToken(User user) {
        String jti = UUID.randomUUID().toString();
        Instant issuedAt = Instant.now();
        Instant expiresAt = issuedAt.plus(accessTokenTtl);

        String token = Jwts.builder()
                .header().keyId(KEY_ID).and()
                .subject(user.getId().toString())
                .claim("email", user.getEmail().value())
                .claim("role", user.getRole().name())
                .id(jti)
                .issuer(issuer)
                .issuedAt(Date.from(issuedAt))
                .expiration(Date.from(expiresAt))
                .signWith(keyPair.getPrivate(), Jwts.SIG.RS256)
                .compact();

        return new IssuedAccessToken(token, jti, issuedAt, expiresAt);
    }

    /**
     * Parses and verifies the signature of an access token, returning its claims.
     * Throws {@link io.jsonwebtoken.JwtException} (e.g. {@link io.jsonwebtoken.ExpiredJwtException},
     * {@link io.jsonwebtoken.security.SignatureException}) if the token is invalid or expired.
     */
    public Jws<Claims> parse(String token) {
        return Jwts.parser()
                .verifyWith(keyPair.getPublic())
                .build()
                .parseSignedClaims(token);
    }

    public Duration accessTokenTtl() {
        return accessTokenTtl;
    }

    /** Returns the JWKS document (RFC 7517) exposing the public key used to verify access tokens. */
    public Map<String, Object> jwks() {
        RSAPublicKey publicKey = (RSAPublicKey) keyPair.getPublic();
        Base64.Encoder encoder = Base64.getUrlEncoder().withoutPadding();
        Map<String, Object> jwk = Map.of(
                "kty", "RSA",
                "use", "sig",
                "alg", "RS256",
                "kid", KEY_ID,
                "n", encoder.encodeToString(unsigned(publicKey.getModulus().toByteArray())),
                "e", encoder.encodeToString(unsigned(publicKey.getPublicExponent().toByteArray())));
        return Map.of("keys", List.of(jwk));
    }

    /** Strips the leading sign byte {@link java.math.BigInteger#toByteArray()} adds for positive values. */
    private static byte[] unsigned(byte[] bytes) {
        if (bytes.length > 1 && bytes[0] == 0) {
            return java.util.Arrays.copyOfRange(bytes, 1, bytes.length);
        }
        return bytes;
    }
}
