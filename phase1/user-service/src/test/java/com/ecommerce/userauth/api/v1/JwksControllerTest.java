package com.ecommerce.userauth.api.v1;

import com.ecommerce.userauth.service.JwtService;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class JwksControllerTest {

    private final JwtService jwtService = mock(JwtService.class);
    private final JwksController controller = new JwksController(jwtService);

    @Test
    void returnsTheJwksDocumentFromJwtService() {
        Map<String, Object> jwks = Map.of("keys", java.util.List.of(Map.of("kty", "RSA")));
        when(jwtService.jwks()).thenReturn(jwks);

        Map<String, Object> result = controller.jwks();

        assertThat(result).isEqualTo(jwks);
    }
}
