package com.parentalcontrol.auth.service;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.parentalcontrol.auth.dao.RefreshTokenDao;
import com.parentalcontrol.auth.domain.RefreshToken;
import com.parentalcontrol.auth.dto.RefreshTokenRequest;
import com.parentalcontrol.billing.service.SubscriptionService;
import com.parentalcontrol.common.security.JwtProperties;
import com.parentalcontrol.common.security.JwtTokenProvider;
import com.parentalcontrol.common.security.RateLimitService;
import com.parentalcontrol.config.EmailVerificationProperties;
import com.parentalcontrol.user.dao.ParentProfileDao;
import com.parentalcontrol.user.dao.UserDao;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

/**
 * Logout must revoke only the presented refresh token so parent app and web can stay signed in
 * on the same account at the same time.
 */
@ExtendWith(MockitoExtension.class)
class AuthServiceLogoutTest {

  @Mock private UserDao userRepository;
  @Mock private ParentProfileDao parentProfileRepository;
  @Mock private RefreshTokenDao refreshTokenRepository;
  @Mock private PasswordEncoder passwordEncoder;
  @Mock private JwtTokenProvider jwtTokenProvider;
  @Mock private JwtProperties jwtProperties;
  @Mock private SubscriptionService subscriptionService;
  @Mock private UserTokenInvalidationService userTokenInvalidationService;
  @Mock private EmailVerificationService emailVerificationService;
  @Mock private EmailVerificationProperties emailVerificationProperties;
  @Mock private LoginAttemptService loginAttemptService;
  @Mock private RateLimitService rateLimitService;

  @InjectMocks private AuthService authService;

  @Test
  void logout_revokesOnlyPresentedRefreshToken_doesNotInvalidateAllSessions() {
    String raw = "device-refresh-token";
    String hash = AuthService.hashToken(raw);
    RefreshToken stored =
        RefreshToken.builder()
            .userId(UUID.randomUUID())
            .tokenHash(hash)
            .expiresAt(Instant.now().plusSeconds(3600))
            .revoked(false)
            .build();
    when(refreshTokenRepository.findByTokenHash(hash)).thenReturn(Optional.of(stored));

    RefreshTokenRequest request = new RefreshTokenRequest();
    request.setRefreshToken(raw);
    authService.logout(request);

    ArgumentCaptor<RefreshToken> captor = ArgumentCaptor.forClass(RefreshToken.class);
    verify(refreshTokenRepository).save(captor.capture());
    org.assertj.core.api.Assertions.assertThat(captor.getValue().isRevoked()).isTrue();
    verify(userTokenInvalidationService, never()).invalidateAllSessions(any());
  }
}
