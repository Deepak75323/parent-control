package com.parentalcontrol.auth.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.parentalcontrol.auth.dao.RefreshTokenDao;
import com.parentalcontrol.auth.dto.AuthResponse;
import com.parentalcontrol.auth.dto.LoginRequest;
import com.parentalcontrol.billing.service.SubscriptionService;
import com.parentalcontrol.common.domain.Role;
import com.parentalcontrol.common.exception.BusinessException;
import com.parentalcontrol.common.exception.ErrorCode;
import com.parentalcontrol.common.security.JwtProperties;
import com.parentalcontrol.common.security.JwtTokenProvider;
import com.parentalcontrol.common.security.RateLimitService;
import com.parentalcontrol.config.EmailVerificationProperties;
import com.parentalcontrol.user.dao.ParentProfileDao;
import com.parentalcontrol.user.dao.UserDao;
import com.parentalcontrol.user.domain.ParentProfile;
import com.parentalcontrol.user.domain.User;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

@ExtendWith(MockitoExtension.class)
class AuthServiceLoginTest {

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

  private static final String EMAIL = "parent@example.com";
  private static final String PASSWORD = "SecurePass123!";
  private static final String PASSWORD_HASH = "$2a$12$TESTHASH";

  // -----------------------------------------------------------------------
  // Helpers
  // -----------------------------------------------------------------------

  private User buildUser(boolean emailVerified) {
    User user = new User();
    user.setId(UUID.randomUUID());
    user.setEmail(EMAIL);
    user.setPasswordHash(PASSWORD_HASH);
    user.setRole(Role.ROLE_PARENT);
    user.setEmailVerified(emailVerified);
    user.setConsentAccepted(true);
    return user;
  }

  private ParentProfile buildProfile(User user) {
    ParentProfile profile = new ParentProfile();
    profile.setId(UUID.randomUUID());
    profile.setUser(user);
    profile.setFirstName("Test");
    profile.setLastName("User");
    return profile;
  }

  private void stubSuccessfulJwtResponse() {
    when(jwtTokenProvider.createAccessToken(any(), anyString(), any()))
        .thenReturn("test-access-token");
    when(jwtTokenProvider.createRefreshToken(any(), anyString(), any()))
        .thenReturn("test-refresh-token");
    when(jwtProperties.getRefreshTokenExpirationMs()).thenReturn(86_400_000L);
    when(jwtProperties.getAccessTokenExpirationMs()).thenReturn(3_600_000L);
    when(refreshTokenRepository.save(any())).thenReturn(null);
  }

  private static LoginRequest loginRequest(String email, String password) {
    LoginRequest req = new LoginRequest();
    req.setEmail(email);
    req.setPassword(password);
    return req;
  }

  // -----------------------------------------------------------------------
  // Tests — email verification enforcement
  // -----------------------------------------------------------------------

  @Test
  void login_verifiedUser_enforcementEnabled_succeeds() {
    User user = buildUser(true);
    ParentProfile profile = buildProfile(user);

    when(userRepository.findByEmailIgnoreCase(EMAIL)).thenReturn(Optional.of(user));
    when(passwordEncoder.matches(PASSWORD, PASSWORD_HASH)).thenReturn(true);
    when(emailVerificationProperties.isEnforcementEnabled()).thenReturn(true);
    when(parentProfileRepository.findByUser_Id(user.getId())).thenReturn(Optional.of(profile));
    stubSuccessfulJwtResponse();

    AuthResponse response = authService.login(loginRequest(EMAIL, PASSWORD));

    assertThat(response.getAccessToken()).isEqualTo("test-access-token");
    assertThat(response.getRefreshToken()).isEqualTo("test-refresh-token");
    assertThat(response.getProfile()).isNotNull();
    assertThat(response.getProfile().getId()).isEqualTo(profile.getId());
    assertThat(response.getProfile().getUserId()).isEqualTo(user.getId());
  }

  @Test
  void login_unverifiedUser_enforcementEnabled_throwsEmailNotVerified() {
    User user = buildUser(false);

    when(userRepository.findByEmailIgnoreCase(EMAIL)).thenReturn(Optional.of(user));
    when(passwordEncoder.matches(PASSWORD, PASSWORD_HASH)).thenReturn(true);
    when(emailVerificationProperties.isEnforcementEnabled()).thenReturn(true);

    assertThatThrownBy(() -> authService.login(loginRequest(EMAIL, PASSWORD)))
        .isInstanceOf(BusinessException.class)
        .satisfies(
            ex -> {
              BusinessException bex = (BusinessException) ex;
              assertThat(bex.getErrorCode()).isEqualTo(ErrorCode.EMAIL_NOT_VERIFIED);
              assertThat(bex.getMessage()).contains("verification link");
            });

    // Profile lookup and token creation must never happen on rejection
    verify(parentProfileRepository, never()).findByUser_Id(any());
    verify(jwtTokenProvider, never()).createAccessToken(any(), any(), any());
    verify(refreshTokenRepository, never()).save(any());
  }

  @Test
  void login_unverifiedUser_enforcementDisabled_succeeds() {
    User user = buildUser(false);
    ParentProfile profile = buildProfile(user);

    when(userRepository.findByEmailIgnoreCase(EMAIL)).thenReturn(Optional.of(user));
    when(passwordEncoder.matches(PASSWORD, PASSWORD_HASH)).thenReturn(true);
    when(emailVerificationProperties.isEnforcementEnabled()).thenReturn(false);
    when(parentProfileRepository.findByUser_Id(user.getId())).thenReturn(Optional.of(profile));
    stubSuccessfulJwtResponse();

    AuthResponse response = authService.login(loginRequest(EMAIL, PASSWORD));

    assertThat(response.getAccessToken()).isEqualTo("test-access-token");
  }

  // -----------------------------------------------------------------------
  // Tests — per-account lockout integration
  // -----------------------------------------------------------------------

  @Test
  void login_whenAccountLocked_throwsAccountLocked() {
    User user = buildUser(true);
    when(userRepository.findByEmailIgnoreCase(EMAIL)).thenReturn(Optional.of(user));
    doThrow(new BusinessException(ErrorCode.ACCOUNT_LOCKED, "locked"))
        .when(loginAttemptService)
        .assertNotLocked(EMAIL);

    assertThatThrownBy(() -> authService.login(loginRequest(EMAIL, PASSWORD)))
        .isInstanceOf(BusinessException.class)
        .satisfies(
            ex ->
                assertThat(((BusinessException) ex).getErrorCode())
                    .isEqualTo(ErrorCode.ACCOUNT_LOCKED));

    verify(passwordEncoder, never()).matches(any(), any());
    verify(jwtTokenProvider, never()).createAccessToken(any(), any(), any());
  }

  @Test
  void login_wrongPassword_recordsFailure() {
    User user = buildUser(true);
    when(userRepository.findByEmailIgnoreCase(EMAIL)).thenReturn(Optional.of(user));
    when(passwordEncoder.matches(PASSWORD, PASSWORD_HASH)).thenReturn(false);

    assertThatThrownBy(() -> authService.login(loginRequest(EMAIL, PASSWORD)))
        .isInstanceOf(BusinessException.class)
        .satisfies(
            ex ->
                assertThat(((BusinessException) ex).getErrorCode())
                    .isEqualTo(ErrorCode.INVALID_CREDENTIALS));

    verify(loginAttemptService).recordFailure(EMAIL);
    verify(loginAttemptService, never()).recordSuccess(any());
  }

  @Test
  void login_successfulLogin_recordsSuccessAndClearsLockout() {
    User user = buildUser(true);
    ParentProfile profile = buildProfile(user);
    when(userRepository.findByEmailIgnoreCase(EMAIL)).thenReturn(Optional.of(user));
    when(passwordEncoder.matches(PASSWORD, PASSWORD_HASH)).thenReturn(true);
    when(emailVerificationProperties.isEnforcementEnabled()).thenReturn(false);
    when(parentProfileRepository.findByUser_Id(user.getId())).thenReturn(Optional.of(profile));
    stubSuccessfulJwtResponse();

    authService.login(loginRequest(EMAIL, PASSWORD));

    verify(loginAttemptService).assertNotLocked(EMAIL);
    verify(loginAttemptService).recordSuccess(EMAIL);
    verify(loginAttemptService, never()).recordFailure(any());
  }

  @Test
  void login_unknownEmail_doesNotCheckLockout() {
    when(userRepository.findByEmailIgnoreCase(EMAIL)).thenReturn(Optional.empty());

    assertThatThrownBy(() -> authService.login(loginRequest(EMAIL, PASSWORD)))
        .isInstanceOf(BusinessException.class)
        .satisfies(
            ex ->
                assertThat(((BusinessException) ex).getErrorCode())
                    .isEqualTo(ErrorCode.INVALID_CREDENTIALS));

    // lockout not checked for unknown emails — no DoS amplification
    verify(loginAttemptService, never()).assertNotLocked(any());
    verify(loginAttemptService, never()).recordFailure(any());
  }

  // -----------------------------------------------------------------------
  // Tests — existing credential validation behaviour preserved
  // -----------------------------------------------------------------------

  @Test
  void login_wrongPassword_throwsInvalidCredentials() {
    User user = buildUser(true);

    when(userRepository.findByEmailIgnoreCase(EMAIL)).thenReturn(Optional.of(user));
    when(passwordEncoder.matches(PASSWORD, PASSWORD_HASH)).thenReturn(false);

    assertThatThrownBy(() -> authService.login(loginRequest(EMAIL, PASSWORD)))
        .isInstanceOf(BusinessException.class)
        .satisfies(
            ex ->
                assertThat(((BusinessException) ex).getErrorCode())
                    .isEqualTo(ErrorCode.INVALID_CREDENTIALS));

    verify(emailVerificationProperties, never()).isEnforcementEnabled();
    verify(jwtTokenProvider, never()).createAccessToken(any(), any(), any());
  }

  @Test
  void login_unknownEmail_throwsInvalidCredentials() {
    when(userRepository.findByEmailIgnoreCase(EMAIL)).thenReturn(Optional.empty());

    assertThatThrownBy(() -> authService.login(loginRequest(EMAIL, PASSWORD)))
        .isInstanceOf(BusinessException.class)
        .satisfies(
            ex ->
                assertThat(((BusinessException) ex).getErrorCode())
                    .isEqualTo(ErrorCode.INVALID_CREDENTIALS));

    verify(passwordEncoder, never()).matches(any(), any());
    verify(jwtTokenProvider, never()).createAccessToken(any(), any(), any());
  }

  @Test
  void login_emailNotVerified_errorCode_isForbiddenHttpStatus() {
    assertThat(ErrorCode.EMAIL_NOT_VERIFIED.getStatus().value()).isEqualTo(403);
    assertThat(ErrorCode.EMAIL_NOT_VERIFIED.getCode()).isEqualTo("EMAIL_NOT_VERIFIED");
  }
}
