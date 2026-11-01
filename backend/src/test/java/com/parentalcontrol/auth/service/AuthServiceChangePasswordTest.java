package com.parentalcontrol.auth.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.parentalcontrol.auth.dao.RefreshTokenDao;
import com.parentalcontrol.auth.dto.ChangePasswordRequest;
import com.parentalcontrol.billing.service.SubscriptionService;
import com.parentalcontrol.common.constant.MessageConstants;
import com.parentalcontrol.common.domain.Role;
import com.parentalcontrol.common.exception.BusinessException;
import com.parentalcontrol.common.exception.ErrorCode;
import com.parentalcontrol.common.security.JwtProperties;
import com.parentalcontrol.common.security.JwtTokenProvider;
import com.parentalcontrol.common.security.RateLimitService;
import com.parentalcontrol.common.security.SecurityUtils;
import com.parentalcontrol.config.EmailVerificationProperties;
import com.parentalcontrol.user.dao.ParentProfileDao;
import com.parentalcontrol.user.dao.UserDao;
import com.parentalcontrol.user.domain.User;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

@ExtendWith(MockitoExtension.class)
class AuthServiceChangePasswordTest {

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
  @Mock private com.parentalcontrol.audit.service.AuditService auditService;

  @InjectMocks private AuthService authService;

  private MockedStatic<SecurityUtils> security;
  private final UUID userId = UUID.randomUUID();

  @BeforeEach
  void setUp() {
    security = org.mockito.Mockito.mockStatic(SecurityUtils.class);
    security.when(SecurityUtils::requireUserId).thenReturn(userId);
  }

  @AfterEach
  void tearDown() {
    security.close();
  }

  @Test
  void changePasswordUpdatesHashAndInvalidatesSessions() {
    User user = user(userId);
    when(userRepository.findById(userId)).thenReturn(Optional.of(user));
    when(passwordEncoder.matches("old-pass-word", user.getPasswordHash())).thenReturn(true);
    when(passwordEncoder.matches("new-pass-word", user.getPasswordHash())).thenReturn(false);
    when(passwordEncoder.encode("new-pass-word")).thenReturn("$2a$12$NEWHASH");

    ChangePasswordRequest request = request("old-pass-word", "new-pass-word");
    String message = authService.changePassword(request);

    assertThat(message).isEqualTo(MessageConstants.PASSWORD_UPDATED_SIGN_IN_AGAIN);
    ArgumentCaptor<User> saved = ArgumentCaptor.forClass(User.class);
    verify(userRepository).save(saved.capture());
    assertThat(saved.getValue().getPasswordHash()).isEqualTo("$2a$12$NEWHASH");
    verify(userTokenInvalidationService).invalidateAllSessions(userId);
  }

  @Test
  void changePasswordRejectsWrongCurrentPassword() {
    User user = user(userId);
    when(userRepository.findById(userId)).thenReturn(Optional.of(user));
    when(passwordEncoder.matches("wrong-password", user.getPasswordHash())).thenReturn(false);

    assertThatThrownBy(() -> authService.changePassword(request("wrong-password", "new-pass-word")))
        .isInstanceOf(BusinessException.class)
        .satisfies(
            ex -> {
              BusinessException be = (BusinessException) ex;
              assertThat(be.getErrorCode()).isEqualTo(ErrorCode.INVALID_CREDENTIALS);
              assertThat(be.getMessage()).isEqualTo(MessageConstants.INCORRECT_PASSWORD);
            });
    verify(userRepository, never()).save(any());
    verify(userTokenInvalidationService, never()).invalidateAllSessions(any());
  }

  @Test
  void changePasswordRejectsSamePassword() {
    User user = user(userId);
    when(userRepository.findById(userId)).thenReturn(Optional.of(user));
    when(passwordEncoder.matches("same-pass-word", user.getPasswordHash())).thenReturn(true);

    assertThatThrownBy(
            () -> authService.changePassword(request("same-pass-word", "same-pass-word")))
        .isInstanceOf(BusinessException.class)
        .satisfies(
            ex -> {
              BusinessException be = (BusinessException) ex;
              assertThat(be.getErrorCode()).isEqualTo(ErrorCode.VALIDATION_ERROR);
              assertThat(be.getMessage())
                  .isEqualTo(MessageConstants.NEW_PASSWORD_MUST_BE_DIFFERENT);
            });
    verify(userRepository, never()).save(any());
  }

  private static User user(UUID id) {
    User user = new User();
    user.setId(id);
    user.setEmail("parent@example.com");
    user.setPasswordHash("$2a$12$OLDHASH");
    user.setRole(Role.ROLE_PARENT);
    return user;
  }

  private static ChangePasswordRequest request(String current, String next) {
    ChangePasswordRequest req = new ChangePasswordRequest();
    req.setCurrentPassword(current);
    req.setNewPassword(next);
    return req;
  }
}
