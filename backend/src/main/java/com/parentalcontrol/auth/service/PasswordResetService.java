package com.parentalcontrol.auth.service;

import com.parentalcontrol.auth.dao.PasswordResetTokenDao;
import com.parentalcontrol.auth.domain.PasswordResetToken;
import com.parentalcontrol.auth.dto.ForgotPasswordRequest;
import com.parentalcontrol.auth.dto.ResetPasswordRequest;
import com.parentalcontrol.common.constant.MessageConstants;
import com.parentalcontrol.common.exception.BusinessException;
import com.parentalcontrol.common.exception.ErrorCode;
import com.parentalcontrol.common.util.SensitiveDataRedactor;
import com.parentalcontrol.config.PasswordResetProperties;
import com.parentalcontrol.user.dao.UserDao;
import com.parentalcontrol.user.domain.User;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Slf4j
public class PasswordResetService {

  private static final SecureRandom SECURE_RANDOM = new SecureRandom();
  private static final String GENERIC_FORGOT_MESSAGE =
      MessageConstants.IF_AN_ACCOUNT_EXISTS_FOR_THAT_EMAIL_A_RESET_LINK_HAS_BEEN_SENT;

  private final UserDao userRepository;
  private final PasswordResetTokenDao passwordResetTokenRepository;
  private final PasswordEncoder passwordEncoder;
  private final PasswordResetProperties passwordResetProperties;
  private final EmailSender emailSender;
  private final UserTokenInvalidationService userTokenInvalidationService;

  @Transactional
  public String requestReset(ForgotPasswordRequest request) {
    if (!passwordResetProperties.isEnabled()) {
      throw new BusinessException(ErrorCode.FORBIDDEN, MessageConstants.PASSWORD_RESET_IS_DISABLED);
    }
    String normalizedEmail = normalizeEmail(request.getEmail());
    log.info("Password reset requested for email={}", SensitiveDataRedactor.email(normalizedEmail));
    Optional<User> userOpt = userRepository.findByEmailIgnoreCase(normalizedEmail);
    if (userOpt.isEmpty()) {
      return GENERIC_FORGOT_MESSAGE;
    }
    User user = userOpt.get();
    String rawToken = generateToken();
    Instant now = Instant.now();
    passwordResetTokenRepository.invalidateActiveTokensForUser(user.getId(), now);
    PasswordResetToken entity =
        PasswordResetToken.builder()
            .userId(user.getId())
            .tokenHash(AuthService.hashToken(rawToken))
            .expiresAt(now.plusSeconds(passwordResetProperties.getTokenTtlMinutes() * 60L))
            .build();
    passwordResetTokenRepository.save(entity);
    String resetLink = buildResetLink(rawToken);
    emailSender.sendPasswordResetEmail(user.getEmail(), resetLink);
    return GENERIC_FORGOT_MESSAGE;
  }

  @Transactional
  public String resetPassword(ResetPasswordRequest request) {
    if (!passwordResetProperties.isEnabled()) {
      throw new BusinessException(ErrorCode.FORBIDDEN, MessageConstants.PASSWORD_RESET_IS_DISABLED);
    }
    String tokenHash = AuthService.hashToken(request.getToken().trim());
    Instant now = Instant.now();
    int claimed = passwordResetTokenRepository.claimToken(tokenHash, now);
    if (claimed != 1) {
      throw new BusinessException(
          ErrorCode.UNAUTHORIZED, MessageConstants.INVALID_OR_EXPIRED_RESET_LINK);
    }
    PasswordResetToken stored =
        passwordResetTokenRepository
            .findByTokenHash(tokenHash)
            .orElseThrow(
                () ->
                    new BusinessException(
                        ErrorCode.UNAUTHORIZED, MessageConstants.INVALID_OR_EXPIRED_RESET_LINK));
    User user =
        userRepository
            .findById(stored.getUserId())
            .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND));
    user.setPasswordHash(passwordEncoder.encode(request.getPassword()));
    userRepository.save(user);
    userTokenInvalidationService.invalidateAllSessions(user.getId());

    log.info("Password reset completed for userId={}", user.getId());
    return "Password updated. Sign in with your new password.";
  }

  private String buildResetLink(String rawToken) {
    String base = passwordResetProperties.getResetUrlBase();
    if (base == null || base.isBlank()) {
      return "parentalcontrol://reset-password?token=" + rawToken;
    }
    String trimmed = base.endsWith("/") ? base.substring(0, base.length() - 1) : base;
    return trimmed + "?token=" + rawToken;
  }

  private static String generateToken() {
    byte[] bytes = new byte[32];
    SECURE_RANDOM.nextBytes(bytes);
    return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
  }

  private static String normalizeEmail(String email) {
    if (email == null) {
      return "";
    }
    return email.trim().toLowerCase();
  }
}
