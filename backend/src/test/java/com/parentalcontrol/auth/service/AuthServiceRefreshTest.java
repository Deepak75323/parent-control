package com.parentalcontrol.auth.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.parentalcontrol.auth.dao.RefreshTokenDao;
import com.parentalcontrol.auth.domain.RefreshToken;
import com.parentalcontrol.auth.dto.AuthResponse;
import com.parentalcontrol.auth.dto.RefreshTokenRequest;
import com.parentalcontrol.billing.service.SubscriptionService;
import com.parentalcontrol.common.domain.Role;
import com.parentalcontrol.common.domain.TokenType;
import com.parentalcontrol.common.exception.BusinessException;
import com.parentalcontrol.common.exception.ErrorCode;
import com.parentalcontrol.common.security.JwtProperties;
import com.parentalcontrol.common.security.JwtTokenProvider;
import com.parentalcontrol.config.EmailVerificationProperties;
import com.parentalcontrol.user.dao.ParentProfileDao;
import com.parentalcontrol.user.dao.UserDao;
import com.parentalcontrol.user.domain.ParentProfile;
import com.parentalcontrol.user.domain.User;
import io.jsonwebtoken.Claims;
import jakarta.persistence.LockTimeoutException;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.CannotAcquireLockException;
import org.springframework.security.crypto.password.PasswordEncoder;

/**
 * Unit tests for {@link AuthService#refresh} covering the P1-06 fix: single locked query, reuse
 * detection, expiry, and lock-timeout handling.
 */
@ExtendWith(MockitoExtension.class)
class AuthServiceRefreshTest {

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
  @Mock private Claims claims;

  @InjectMocks private AuthService authService;

  private static final String RAW_TOKEN = "raw.refresh.token";
  private static final UUID USER_ID = UUID.randomUUID();
  private static final UUID PROFILE_ID = UUID.randomUUID();

  private String hash;

  @BeforeEach
  void setUp() {
    hash = AuthService.hashToken(RAW_TOKEN);
  }

  // -----------------------------------------------------------------------
  // Token not found
  // -----------------------------------------------------------------------

  @Test
  void refresh_tokenNotFound_throwsUnauthorized() {
    when(refreshTokenRepository.findByTokenHashForUpdate(hash)).thenReturn(Optional.empty());

    assertThatThrownBy(() -> authService.refresh(request(RAW_TOKEN)))
        .isInstanceOf(BusinessException.class)
        .satisfies(
            ex ->
                assertThat(((BusinessException) ex).getErrorCode())
                    .isEqualTo(ErrorCode.UNAUTHORIZED));

    verify(refreshTokenRepository).findByTokenHashForUpdate(hash);
    // Old two-query pattern must NOT be used
    verify(refreshTokenRepository, never()).findByTokenHash(anyString());
    verify(refreshTokenRepository, never()).findByTokenHashAndRevokedFalse(anyString());
  }

  // -----------------------------------------------------------------------
  // Revoked token — replay / reuse detection
  // -----------------------------------------------------------------------

  @Test
  void refresh_revokedToken_triggersSessionInvalidationAndThrowsUnauthorized() {
    RefreshToken revoked = buildToken(true, Instant.now().plusSeconds(3600));
    when(refreshTokenRepository.findByTokenHashForUpdate(hash)).thenReturn(Optional.of(revoked));

    assertThatThrownBy(() -> authService.refresh(request(RAW_TOKEN)))
        .isInstanceOf(BusinessException.class)
        .satisfies(
            ex ->
                assertThat(((BusinessException) ex).getErrorCode())
                    .isEqualTo(ErrorCode.UNAUTHORIZED));

    verify(userTokenInvalidationService).invalidateAllSessions(revoked.getUserId());
    verify(jwtTokenProvider, never()).validateToken(anyString());
  }

  @Test
  void refresh_revokedToken_httpStatus_isUnauthorized() {
    assertThat(ErrorCode.UNAUTHORIZED.getStatus().value()).isEqualTo(401);
  }

  // -----------------------------------------------------------------------
  // Expired token
  // -----------------------------------------------------------------------

  @Test
  void refresh_expiredToken_throwsUnauthorized() {
    RefreshToken expired = buildToken(false, Instant.now().minusSeconds(60));
    when(refreshTokenRepository.findByTokenHashForUpdate(hash)).thenReturn(Optional.of(expired));

    assertThatThrownBy(() -> authService.refresh(request(RAW_TOKEN)))
        .isInstanceOf(BusinessException.class)
        .satisfies(
            ex -> {
              assertThat(((BusinessException) ex).getErrorCode()).isEqualTo(ErrorCode.UNAUTHORIZED);
              assertThat(ex.getMessage()).contains("expired");
            });

    verify(userTokenInvalidationService, never()).invalidateAllSessions(any());
  }

  // -----------------------------------------------------------------------
  // Invalid JWT signature / type
  // -----------------------------------------------------------------------

  @Test
  void refresh_invalidJwtSignature_throwsUnauthorized() {
    RefreshToken active = buildToken(false, Instant.now().plusSeconds(3600));
    when(refreshTokenRepository.findByTokenHashForUpdate(hash)).thenReturn(Optional.of(active));
    when(jwtTokenProvider.validateToken(RAW_TOKEN)).thenReturn(false);

    assertThatThrownBy(() -> authService.refresh(request(RAW_TOKEN)))
        .isInstanceOf(BusinessException.class)
        .satisfies(
            ex ->
                assertThat(((BusinessException) ex).getErrorCode())
                    .isEqualTo(ErrorCode.UNAUTHORIZED));
  }

  @Test
  void refresh_wrongTokenType_throwsUnauthorized() {
    RefreshToken active = buildToken(false, Instant.now().plusSeconds(3600));
    when(refreshTokenRepository.findByTokenHashForUpdate(hash)).thenReturn(Optional.of(active));
    when(jwtTokenProvider.validateToken(RAW_TOKEN)).thenReturn(true);
    when(jwtTokenProvider.parseClaims(RAW_TOKEN)).thenReturn(claims);
    when(jwtTokenProvider.getTokenType(claims)).thenReturn(TokenType.ACCESS);

    assertThatThrownBy(() -> authService.refresh(request(RAW_TOKEN)))
        .isInstanceOf(BusinessException.class)
        .satisfies(
            ex ->
                assertThat(((BusinessException) ex).getErrorCode())
                    .isEqualTo(ErrorCode.UNAUTHORIZED));
  }

  // -----------------------------------------------------------------------
  // Lock timeout — concurrent request could not acquire the row lock in time
  // -----------------------------------------------------------------------

  @Test
  void refresh_lockTimeout_throwsTooManyRequests() {
    when(refreshTokenRepository.findByTokenHashForUpdate(hash))
        .thenThrow(new LockTimeoutException("lock timeout"));

    assertThatThrownBy(() -> authService.refresh(request(RAW_TOKEN)))
        .isInstanceOf(BusinessException.class)
        .satisfies(
            ex ->
                assertThat(((BusinessException) ex).getErrorCode())
                    .isEqualTo(ErrorCode.TOO_MANY_REQUESTS));
  }

  @Test
  void refresh_cannotAcquireLock_throwsTooManyRequests() {
    when(refreshTokenRepository.findByTokenHashForUpdate(hash))
        .thenThrow(new CannotAcquireLockException("cannot acquire lock"));

    assertThatThrownBy(() -> authService.refresh(request(RAW_TOKEN)))
        .isInstanceOf(BusinessException.class)
        .satisfies(
            ex ->
                assertThat(((BusinessException) ex).getErrorCode())
                    .isEqualTo(ErrorCode.TOO_MANY_REQUESTS));
  }

  // -----------------------------------------------------------------------
  // Successful refresh
  // -----------------------------------------------------------------------

  @Test
  void refresh_activeToken_revokesOldAndReturnsNewTokens() {
    RefreshToken active = buildToken(false, Instant.now().plusSeconds(3600));
    User user = buildUser();
    ParentProfile profile = buildProfile(user);

    when(refreshTokenRepository.findByTokenHashForUpdate(hash)).thenReturn(Optional.of(active));
    when(jwtTokenProvider.validateToken(RAW_TOKEN)).thenReturn(true);
    when(jwtTokenProvider.parseClaims(RAW_TOKEN)).thenReturn(claims);
    when(jwtTokenProvider.getTokenType(claims)).thenReturn(TokenType.REFRESH);
    when(jwtTokenProvider.getUserId(claims)).thenReturn(USER_ID);
    when(userRepository.findById(USER_ID)).thenReturn(Optional.of(user));
    when(parentProfileRepository.findByUser_Id(USER_ID)).thenReturn(Optional.of(profile));
    when(jwtTokenProvider.createAccessToken(any(), any(), any())).thenReturn("new.access.token");
    when(jwtTokenProvider.createRefreshToken(any(), any(), any())).thenReturn("new.refresh.token");
    when(jwtProperties.getAccessTokenExpirationMs()).thenReturn(900_000L);
    when(jwtProperties.getRefreshTokenExpirationMs()).thenReturn(86_400_000L);
    when(refreshTokenRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

    AuthResponse response = authService.refresh(request(RAW_TOKEN));

    assertThat(response.getAccessToken()).isEqualTo("new.access.token");
    assertThat(response.getRefreshToken()).isEqualTo("new.refresh.token");
    assertThat(active.isRevoked()).isTrue(); // old token was revoked
    verify(refreshTokenRepository).findByTokenHashForUpdate(hash);
    // Old two-query pattern must NOT be used
    verify(refreshTokenRepository, never()).findByTokenHash(anyString());
    verify(refreshTokenRepository, never()).findByTokenHashAndRevokedFalse(anyString());
  }

  // -----------------------------------------------------------------------
  // Helpers
  // -----------------------------------------------------------------------

  private static RefreshTokenRequest request(String raw) {
    RefreshTokenRequest r = new RefreshTokenRequest();
    r.setRefreshToken(raw);
    return r;
  }

  private RefreshToken buildToken(boolean revoked, Instant expiresAt) {
    return RefreshToken.builder()
        .userId(USER_ID)
        .tokenHash(hash)
        .expiresAt(expiresAt)
        .revoked(revoked)
        .build();
  }

  private User buildUser() {
    User u = new User();
    u.setId(USER_ID);
    u.setEmail("parent@example.com");
    u.setRole(Role.ROLE_PARENT);
    u.setEmailVerified(true);
    return u;
  }

  private ParentProfile buildProfile(User user) {
    ParentProfile p = ParentProfile.builder().user(user).firstName("Test").lastName("User").build();
    p.setId(PROFILE_ID);
    return p;
  }
}
