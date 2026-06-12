package com.ecommerce.userauth.config;

import com.ecommerce.userauth.repository.TokenBlacklistRepository;
import jakarta.servlet.FilterChain;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

import java.time.Instant;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class JwtBlacklistFilterTest {

    private final TokenBlacklistRepository blacklist = mock(TokenBlacklistRepository.class);
    private final JwtBlacklistFilter filter = new JwtBlacklistFilter(blacklist);
    private final HttpServletRequest request = mock(HttpServletRequest.class);
    private final HttpServletResponse response = mock(HttpServletResponse.class);
    private final FilterChain chain = mock(FilterChain.class);

    @AfterEach
    void clearContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void allowsRequestWhenJwtIsNotBlacklisted() throws Exception {
        authenticateWithJwt("jti-1");
        when(blacklist.isBlacklisted("jti-1")).thenReturn(false);

        filter.doFilter(request, response, chain);

        verify(chain).doFilter(request, response);
    }

    @Test
    void rejectsRequestWhenJwtIsBlacklisted() throws Exception {
        authenticateWithJwt("jti-2");
        when(blacklist.isBlacklisted("jti-2")).thenReturn(true);

        filter.doFilter(request, response, chain);

        verify(response).sendError(HttpServletResponse.SC_UNAUTHORIZED, "Token revoked");
        verify(chain, org.mockito.Mockito.never()).doFilter(any(), any());
    }

    @Test
    void failsOpenWhenBlacklistCheckThrows() throws Exception {
        authenticateWithJwt("jti-3");
        when(blacklist.isBlacklisted("jti-3")).thenThrow(new RuntimeException("redis down"));

        filter.doFilter(request, response, chain);

        verify(chain).doFilter(request, response);
    }

    @Test
    void passesThroughWhenAuthenticationIsNotAJwt() throws Exception {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken("user", "password"));

        filter.doFilter(request, response, chain);

        verify(chain).doFilter(request, response);
        verifyNoInteractions(blacklist);
    }

    @Test
    void passesThroughWhenUnauthenticated() throws Exception {
        filter.doFilter(request, response, chain);

        verify(chain).doFilter(request, response);
        verifyNoInteractions(blacklist);
    }

    private void authenticateWithJwt(String jti) {
        Jwt jwt = Jwt.withTokenValue("token")
                .header("alg", "RS256")
                .claim("jti", jti)
                .claim("sub", "user-id")
                .issuedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(900))
                .build();
        JwtAuthenticationToken authentication = new JwtAuthenticationToken(jwt);
        SecurityContextHolder.getContext().setAuthentication(authentication);
    }
}
