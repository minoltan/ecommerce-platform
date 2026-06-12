package com.ecommerce.userauth.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.NoSuchAlgorithmException;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;

/**
 * Supplies the RSA key pair used to sign and verify JWT access tokens (ADR-0011).
 * Configured keys ({@code jwt.private-key} / {@code jwt.public-key}, base64-encoded
 * PKCS8/X509 DER) are used when present; otherwise an ephemeral RSA-2048 key pair is
 * generated for dev/test, which means JWKS published by this instance are not stable
 * across restarts.
 */
@Configuration
public class JwtKeyConfig {

    @Bean
    public KeyPair jwtKeyPair(@Value("${jwt.private-key:}") String privateKeyBase64,
                               @Value("${jwt.public-key:}") String publicKeyBase64)
            throws NoSuchAlgorithmException, InvalidKeySpecException {
        if (!privateKeyBase64.isBlank() && !publicKeyBase64.isBlank()) {
            return loadFromConfig(privateKeyBase64, publicKeyBase64);
        }
        return KeyPairGenerator.getInstance("RSA").generateKeyPair();
    }

    private KeyPair loadFromConfig(String privateKeyBase64, String publicKeyBase64)
            throws NoSuchAlgorithmException, InvalidKeySpecException {
        KeyFactory factory = KeyFactory.getInstance("RSA");
        var privateKey = factory.generatePrivate(
                new PKCS8EncodedKeySpec(Base64.getDecoder().decode(privateKeyBase64)));
        var publicKey = factory.generatePublic(
                new X509EncodedKeySpec(Base64.getDecoder().decode(publicKeyBase64)));
        return new KeyPair(publicKey, privateKey);
    }
}
