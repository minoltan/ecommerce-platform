package com.ecommerce.userauth.api.v1;

import com.ecommerce.userauth.service.JwtService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/** Publishes the JWKS used by other services to verify this service's RS256 access tokens. */
@RestController
@RequestMapping("/v1/auth")
public class JwksController {

    private final JwtService jwtService;

    public JwksController(JwtService jwtService) {
        this.jwtService = jwtService;
    }

    @GetMapping("/.well-known/jwks.json")
    public Map<String, Object> jwks() {
        return jwtService.jwks();
    }
}
