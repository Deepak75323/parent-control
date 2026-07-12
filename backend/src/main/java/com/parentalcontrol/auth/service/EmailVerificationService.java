package com.parentalcontrol.auth.service;

import com.parentalcontrol.auth.dao.EmailVerificationTokenDao;
import com.parentalcontrol.auth.domain.EmailVerificationToken;
import com.parentalcontrol.billing.service.SubscriptionService;
import com.parentalcontrol.common.constant.MessageConstants;
import com.parentalcontrol.common.exception.BusinessException;
import com.parentalcontrol.common.exception.ErrorCode;
import com.parentalcontrol.common.security.SecurityUtils;
import com.parentalcontrol.common.util.SensitiveDataRedactor;
import com.parentalcontrol.config.EmailVerificationProperties;
import com.parentalcontrol.user.dao.ParentProfileDao;
import com.parentalcontrol.user.dao.UserDao;
import com.parentalcontrol.user.domain.ParentProfile;
import com.parentalcontrol.user.domain.User;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Slf4j
public class EmailVerificationService {

  private static final SecureRandom SECURE_RANDOM = new SecureRandom();

  private final UserDao userRepository;
  private final ParentProfileDao parentProfileRepository;
  private final EmailVerificationTokenDao emailVerificationTokenRepository;
  private final EmailVerificationProperties emailVerificationProperties;
  private final EmailSender emailSender;
  private final SubscriptionService subscriptionService;

  @Transactional
  public void sendVerificationEmail(User user) {
    if (user.isEmailVerified()) {
      return;
    }
    String rawToken = generateToken();
    Instant now = Instant.now();
    emailVerificationTokenRepository.invalidateActiveTokensForUser(user.getId(), now);
    EmailVerificationToken entity =
        EmailVerificationToken.builder()
            .userId(user.getId())
            .tokenHash(AuthService.hashToken(rawToken))
            .expiresAt(now.plusSeconds(emailVerificationProperties.getTokenTtlHours() * 3600L))
            .build();
    emailVerificationTokenRepository.save(entity);
    String verifyLink = buildVerifyLink(rawToken);
    emailSender.sendEmailVerificationEmail(user.getEmail(), verifyLink);
    log.info("Verification email queued for {}", SensitiveDataRedactor.email(user.getEmail()));
  }

  @Transactional
  public String verifyEmail(String rawToken) {
    String tokenHash = AuthService.hashToken(rawToken.trim());
    Instant now = Instant.now();
    int claimed = emailVerificationTokenRepository.claimToken(tokenHash, now);
    if (claimed != 1) {
      throw new BusinessException(
          ErrorCode.UNAUTHORIZED, MessageConstants.INVALID_OR_EXPIRED_VERIFICATION_LINK);
    }
    EmailVerificationToken stored =
        emailVerificationTokenRepository
            .findByTokenHash(tokenHash)
            .orElseThrow(
                () ->
                    new BusinessException(
                        ErrorCode.UNAUTHORIZED,
                        MessageConstants.INVALID_OR_EXPIRED_VERIFICATION_LINK));
    User user =
        userRepository
            .findById(stored.getUserId())
            .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND));
    if (user.isEmailVerified()) {
      return "Email already verified.";
    }
    user.setEmailVerified(true);
    userRepository.save(user);
    ParentProfile profile =
        parentProfileRepository
            .findByUser_Id(user.getId())
            .orElseThrow(
                () ->
                    new BusinessException(
                        ErrorCode.NOT_FOUND, MessageConstants.PARENT_PROFILE_NOT_FOUND));
    subscriptionService.startTrialIfEligible(profile.getId());
    log.info("Email verified userId={}", user.getId());
    return "Email verified. Your trial has started.";
  }

  @Transactional
  public String resendVerification() {
    UUID userId = SecurityUtils.requireUserId();
    User user =
        userRepository
            .findById(userId)
            .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND));
    if (user.isEmailVerified()) {
      return "Email is already verified.";
    }
    sendVerificationEmail(user);
    return "If your account is unverified, a new verification link has been sent.";
  }

  public boolean isEnforcementEnabled() {
    return emailVerificationProperties.isEnforcementEnabled();
  }

  @Transactional(readOnly = true)
  public boolean isVerifiedForParentProfile(UUID parentProfileId) {
    return parentProfileRepository
        .findUserIdByProfileId(parentProfileId)
        .flatMap(userRepository::findById)
        .map(User::isEmailVerified)
        .orElse(false);
  }

  private String buildVerifyLink(String rawToken) {
    String base = emailVerificationProperties.getVerifyUrlBase();
    if (base == null || base.isBlank()) {
      return "parentalcontrol://verify-email?token=" + rawToken;
    }
    String trimmed = base.endsWith("/") ? base.substring(0, base.length() - 1) : base;
    return trimmed + "?token=" + rawToken;
  }

  private static String generateToken() {
    byte[] bytes = new byte[32];
    SECURE_RANDOM.nextBytes(bytes);
    return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
  }
}
