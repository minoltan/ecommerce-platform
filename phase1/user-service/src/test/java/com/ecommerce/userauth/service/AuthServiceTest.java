package com.ecommerce.userauth.service;

import com.ecommerce.userauth.domain.Email;
import com.ecommerce.userauth.domain.EmailAlreadyRegisteredException;
import com.ecommerce.userauth.domain.EmailVerification;
import com.ecommerce.userauth.domain.IllegalStateTransitionException;
import com.ecommerce.userauth.domain.InvalidCredentialsException;
import com.ecommerce.userauth.domain.InvalidRefreshTokenException;
import com.ecommerce.userauth.domain.InvalidTokenException;
import com.ecommerce.userauth.domain.RateLimitExceededException;
import com.ecommerce.userauth.domain.User;
import com.ecommerce.userauth.repository.EmailVerificationRepository;
import com.ecommerce.userauth.repository.IssuedRefreshToken;
import com.ecommerce.userauth.repository.OutboxEventRepository;
import com.ecommerce.userauth.repository.RateLimitRepository;
import com.ecommerce.userauth.repository.RefreshTokenRepository;
import com.ecommerce.userauth.repository.RotatedRefreshToken;
import com.ecommerce.userauth.repository.TokenBlacklistRepository;
import com.ecommerce.userauth.repository.UserRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock
    private UserRepository userRepository;
    @Mock
    private EmailVerificationRepository emailVerificationRepository;
    @Mock
    private OutboxEventRepository outboxEventRepository;
    @Mock
    private RefreshTokenRepository refreshTokenRepository;
    @Mock
    private TokenBlacklistRepository tokenBlacklistRepository;
    @Mock
    private RateLimitRepository rateLimitRepository;
    @Mock
    private JwtService jwtService;

    private AuthService authService;

    private static final Email EMAIL = new Email("jane.doe@example.com");
    private static final String PASSWORD = "password123";

    @BeforeEach
    void setUp() {
        authService = new AuthService(userRepository, emailVerificationRepository, outboxEventRepository,
                refreshTokenRepository, tokenBlacklistRepository, rateLimitRepository, jwtService,
                new ObjectMapper().registerModule(new JavaTimeModule()));
    }

    @Test
    void registerCreatesUnverifiedUserAndEmailVerificationAndOutboxEvent() {
        when(rateLimitRepository.tryConsume(anyString(), anyLong(), any())).thenReturn(true);
        when(userRepository.existsByEmail(EMAIL)).thenReturn(false);

        UUID userId = authService.register(EMAIL, PASSWORD, "Jane Doe", "203.0.113.1");

        assertThat(userId).isNotNull();
        verify(userRepository).save(any(User.class));
        verify(emailVerificationRepository).save(any(EmailVerification.class));
        verify(outboxEventRepository).save(any());
    }

    @Test
    void registerRejectsDuplicateEmail() {
        when(rateLimitRepository.tryConsume(anyString(), anyLong(), any())).thenReturn(true);
        when(userRepository.existsByEmail(EMAIL)).thenReturn(true);

        assertThatThrownBy(() -> authService.register(EMAIL, PASSWORD, "Jane Doe", "203.0.113.1"))
                .isInstanceOf(EmailAlreadyRegisteredException.class);

        verify(userRepository, never()).save(any());
    }

    @Test
    void registerEnforcesPerIpRateLimit() {
        when(rateLimitRepository.tryConsume(eq(RateLimitRepository.registerKey("203.0.113.1")), anyLong(), any()))
                .thenReturn(false);

        assertThatThrownBy(() -> authService.register(EMAIL, PASSWORD, "Jane Doe", "203.0.113.1"))
                .isInstanceOf(RateLimitExceededException.class);

        verify(userRepository, never()).existsByEmail(any());
    }

    @Test
    void verifyEmailActivatesUserAndIssuesTokens() {
        User user = User.register(EMAIL, PASSWORD, "Jane Doe");
        EmailVerification verification = EmailVerification.issue(user.getId(), Instant.now(), Duration.ofHours(24));

        when(emailVerificationRepository.findByToken(verification.getToken())).thenReturn(Optional.of(verification));
        when(userRepository.findById(user.getId())).thenReturn(Optional.of(user));
        when(jwtService.issueAccessToken(user)).thenReturn(
                new IssuedAccessToken("access-token", "jti-1", Instant.now(), Instant.now().plusSeconds(900)));
        when(refreshTokenRepository.issue(user.getId())).thenReturn(new IssuedRefreshToken("refresh-token", "token-id"));

        IssuedTokens tokens = authService.verifyEmail(verification.getToken());

        assertThat(user.getStatus().name()).isEqualTo("ACTIVE");
        assertThat(tokens.accessToken().token()).isEqualTo("access-token");
        assertThat(tokens.refreshToken().token()).isEqualTo("refresh-token");
    }

    @Test
    void verifyEmailRejectsUnknownToken() {
        when(emailVerificationRepository.findByToken("unknown")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> authService.verifyEmail("unknown"))
                .isInstanceOf(InvalidTokenException.class);
    }

    @Test
    void verifyEmailRejectsExpiredToken() {
        User user = User.register(EMAIL, PASSWORD, "Jane Doe");
        EmailVerification expired = EmailVerification.issue(user.getId(), Instant.now().minus(Duration.ofHours(25)), Duration.ofHours(24));

        when(emailVerificationRepository.findByToken(expired.getToken())).thenReturn(Optional.of(expired));

        assertThatThrownBy(() -> authService.verifyEmail(expired.getToken()))
                .isInstanceOf(InvalidTokenException.class);
    }

    @Test
    void loginWithValidCredentialsIssuesTokensAndResetsRateLimit() {
        User user = User.register(EMAIL, PASSWORD, "Jane Doe");
        user.verifyEmail();

        when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.of(user));
        when(rateLimitRepository.tryConsume(eq(RateLimitRepository.loginKey(user.getId())), anyLong(), any())).thenReturn(true);
        when(jwtService.issueAccessToken(user)).thenReturn(
                new IssuedAccessToken("access-token", "jti-1", Instant.now(), Instant.now().plusSeconds(900)));
        when(refreshTokenRepository.issue(user.getId())).thenReturn(new IssuedRefreshToken("refresh-token", "token-id"));

        IssuedTokens tokens = authService.login(EMAIL, PASSWORD);

        assertThat(tokens.accessToken().token()).isEqualTo("access-token");
        verify(rateLimitRepository).reset(RateLimitRepository.loginKey(user.getId()));
        verify(outboxEventRepository).save(any());
    }

    @Test
    void loginWithUnknownEmailThrowsInvalidCredentials() {
        when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> authService.login(EMAIL, PASSWORD))
                .isInstanceOf(InvalidCredentialsException.class);
    }

    @Test
    void loginWithWrongPasswordThrowsInvalidCredentialsAndDoesNotResetRateLimit() {
        User user = User.register(EMAIL, PASSWORD, "Jane Doe");
        user.verifyEmail();

        when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.of(user));
        when(rateLimitRepository.tryConsume(eq(RateLimitRepository.loginKey(user.getId())), anyLong(), any())).thenReturn(true);

        assertThatThrownBy(() -> authService.login(EMAIL, "wrong-password"))
                .isInstanceOf(InvalidCredentialsException.class);

        verify(rateLimitRepository, never()).reset(any());
        verify(userRepository, never()).save(any());
    }

    @Test
    void loginOnUnverifiedAccountThrowsIllegalStateTransition() {
        User user = User.register(EMAIL, PASSWORD, "Jane Doe");

        when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.of(user));
        when(rateLimitRepository.tryConsume(eq(RateLimitRepository.loginKey(user.getId())), anyLong(), any())).thenReturn(true);

        assertThatThrownBy(() -> authService.login(EMAIL, PASSWORD))
                .isInstanceOf(IllegalStateTransitionException.class);
    }

    @Test
    void loginLockedOutByRateLimitThrowsBeforeCheckingPassword() {
        User user = User.register(EMAIL, PASSWORD, "Jane Doe");
        user.verifyEmail();

        when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.of(user));
        when(rateLimitRepository.tryConsume(eq(RateLimitRepository.loginKey(user.getId())), anyLong(), any())).thenReturn(false);

        assertThatThrownBy(() -> authService.login(EMAIL, PASSWORD))
                .isInstanceOf(RateLimitExceededException.class);

        verify(userRepository, never()).save(any());
    }

    @Test
    void refreshRotatesTokenAndIssuesNewAccessToken() {
        User user = User.register(EMAIL, PASSWORD, "Jane Doe");
        IssuedRefreshToken newRefresh = new IssuedRefreshToken("new-refresh-token", "new-token-id");

        when(refreshTokenRepository.rotate("old-refresh-token"))
                .thenReturn(Optional.of(new RotatedRefreshToken(user.getId(), newRefresh)));
        when(userRepository.findById(user.getId())).thenReturn(Optional.of(user));
        when(jwtService.issueAccessToken(user)).thenReturn(
                new IssuedAccessToken("access-token", "jti-1", Instant.now(), Instant.now().plusSeconds(900)));

        IssuedTokens tokens = authService.refresh("old-refresh-token");

        assertThat(tokens.accessToken().token()).isEqualTo("access-token");
        assertThat(tokens.refreshToken().token()).isEqualTo("new-refresh-token");
    }

    @Test
    void refreshWithInvalidTokenThrows() {
        when(refreshTokenRepository.rotate("bad-token")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> authService.refresh("bad-token"))
                .isInstanceOf(InvalidRefreshTokenException.class);
    }

    @Test
    void logoutRevokesRefreshTokenAndBlacklistsAccessToken() {
        authService.logout("refresh-token", "jti-1", Duration.ofMinutes(10));

        verify(refreshTokenRepository).revoke("refresh-token");
        verify(tokenBlacklistRepository).blacklist("jti-1", Duration.ofMinutes(10));
    }
}
