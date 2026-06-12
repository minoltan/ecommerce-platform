package com.ecommerce.userauth.config;

import com.ecommerce.userauth.repository.TokenBlacklistRepository;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * Rejects requests bearing a JWT whose {@code jti} has been blacklisted (ADR-0011 logout /
 * password-change flows). Runs after JWT signature verification.
 *
 * <p>Per ADR-0011's stated fail-open policy, a Redis outage does not block requests — a
 * revoked-but-not-yet-expired token would only remain usable for the rest of its short
 * (15-minute) lifetime.
 */
public class JwtBlacklistFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(JwtBlacklistFilter.class);

    private final TokenBlacklistRepository blacklist;

    public JwtBlacklistFilter(TokenBlacklistRepository blacklist) {
        this.blacklist = blacklist;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication instanceof JwtAuthenticationToken jwtAuth) {
            Jwt jwt = jwtAuth.getToken();
            try {
                if (blacklist.isBlacklisted(jwt.getId())) {
                    response.sendError(HttpServletResponse.SC_UNAUTHORIZED, "Token revoked");
                    return;
                }
            } catch (Exception e) {
                log.warn("Blacklist check failed, failing open", e);
            }
        }
        chain.doFilter(request, response);
    }
}
