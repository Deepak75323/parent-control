package com.parentalcontrol.auth.service;

import com.parentalcontrol.auth.dao.RefreshTokenDao;
import com.parentalcontrol.auth.domain.RefreshToken;
import com.parentalcontrol.auth.dto.AuthResponse;
import com.parentalcontrol.auth.dto.LoginRequest;
import com.parentalcontrol.auth.dto.RefreshTokenRequest;
import com.parentalcontrol.auth.dto.RegisterRequest;
import com.parentalcontrol.billing.service.SubscriptionService;
import com.parentalcontrol.common.constant.MessageConstants;
import com.parentalcontrol.common.domain.Role;
import com.parentalcontrol.common.domain.TokenType;
import com.parentalcontrol.common.exception.BusinessException;
import com.parentalcontrol.common.exception.ErrorCode;
import com.parentalcontrol.common.security.JwtProperties;
import com.parentalcontrol.common.security.JwtTokenProvider;
import com.parentalcontrol.common.security.RateLimitService;
import com.parentalcontrol.common.util.SensitiveDataRedactor;
import com.parentalcontrol.config.EmailVerificationProperties;
import com.parentalcontrol.user.dao.ParentProfileDao;
import com.parentalcontrol.user.dao.UserDao;
import com.parentalcontrol.user.domain.ParentProfile;
import com.parentalcontrol.user.domain.User;
import com.parentalcontrol.user.dto.ParentProfileResponse;
import io.jsonwebtoken.Claims;
import jakarta.persistence.LockTimeoutException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.HexFormat;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.CannotAcquireLockException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Slf4j
public class AuthService {

  private final UserDao userRepository;
  private final ParentProfileDao parentProfileRepository;
  private final RefreshTokenDao refreshTokenRepository;
  private final PasswordEncoder passwordEncoder;
  private final JwtTokenProvider jwtTokenProvider;
  private final JwtProperties jwtProperties;
  private final SubscriptionService subscriptionService;
  private final UserTokenInvalidationService userTokenInvalidationService;
  private final EmailVerificationService emailVerificationService;
  private final EmailVerificationProperties emailVerificationProperties;
  private final LoginAttemptService loginAttemptService;
  private final RateLimitService rateLimitService;

  @Transactional
  public void logout(RefreshTokenRequest request) {
    String hash = hashToken(request.getRefreshToken());
    refreshTokenRepository
        .findByTokenHash(hash)
        .ifPresent(
            stored -> {
              stored.setRevoked(true);
              refreshTokenRepository.save(stored);
              userTokenInvalidationService.invalidateAllSessions(stored.getUserId());
            });
  }

  @Transactional
  public AuthResponse register(RegisterRequest request) {
    if (!"true".equalsIgnoreCase(request.getConsentAccepted())) {
      throw new BusinessException(
          ErrorCode.VALIDATION_ERROR, MessageConstants.CONSENT_MUST_BE_ACCEPTED);
    }

    String normalizedEmail = normalizeEmail(request.getEmail());
    log.info("[AUTH REGISTER] email={}", SensitiveDataRedactor.email(normalizedEmail));

    boolean exists = userRepository.existsByEmailIgnoreCase(normalizedEmail);
    log.info("[AUTH REGISTER] existsByEmail => {}", exists);
    if (exists) {
      throw new BusinessException(ErrorCode.USER_EXISTS, MessageConstants.EMAIL_ALREADY_REGISTERED);
    }

    User user =
        User.builder()
            .email(normalizedEmail)
            .passwordHash(passwordEncoder.encode(request.getPassword()))
            .role(Role.ROLE_PARENT)
            .consentAccepted(true)
            .build();
    user = userRepository.save(user);
    log.info("[AUTH REGISTER] created userId={}", user.getId());

    ParentProfile profile =
        ParentProfile.builder()
            .user(user)
            .firstName(request.getFirstName().trim())
            .lastName(request.getLastName().trim())
            .phone(request.getPhone())
            .build();
    profile = parentProfileRepository.save(profile);

    if (emailVerificationProperties.isEnforcementEnabled()) {
      emailVerificationService.sendVerificationEmail(user);
    } else {
      user.setEmailVerified(true);
      userRepository.save(user);
      subscriptionService.startTrial(profile.getId());
    }

    return buildAuthResponse(user, profile);
  }

  @Transactional(readOnly = true)
  public AuthResponse login(LoginRequest request) {
    String normalizedEmail = normalizeEmail(request.getEmail());
    log.info("[AUTH LOGIN] email={}", SensitiveDataRedactor.email(normalizedEmail));
    // P2-07: per-email rate limit before any DB work (complements IP limit + lockout).
    rateLimitService.checkAuthEmail(normalizedEmail);
    User user =
        userRepository
            .findByEmailIgnoreCase(normalizedEmail)
            .orElseThrow(() -> new BusinessException(ErrorCode.INVALID_CREDENTIALS));
    // Lockout check after confirming the email exists — prevents DoS on unknown accounts.
    loginAttemptService.assertNotLocked(normalizedEmail);
    if (!passwordEncoder.matches(request.getPassword(), user.getPasswordHash())) {
      loginAttemptService.recordFailure(normalizedEmail);
      throw new BusinessException(ErrorCode.INVALID_CREDENTIALS);
    }
    if (emailVerificationProperties.isEnforcementEnabled() && !user.isEmailVerified()) {
      throw new BusinessException(
          ErrorCode.EMAIL_NOT_VERIFIED,
          MessageConstants
              .EMAIL_ADDRESS_NOT_VERIFIED_PLEASE_CHECK_YOUR_INBOX_FOR_A_VERIFICATION_LINK);
    }
    ParentProfile profile =
        parentProfileRepository
            .findByUser_Id(user.getId())
            .orElseThrow(
                () ->
                    new BusinessException(
                        ErrorCode.NOT_FOUND, MessageConstants.PARENT_PROFILE_NOT_FOUND));

    loginAttemptService.recordSuccess(normalizedEmail);
    return buildAuthResponse(user, profile);
  }

  @Transactional
  public AuthResponse refresh(RefreshTokenRequest request) {
    String hash = hashToken(request.getRefreshToken());

    // Acquire an exclusive row-level lock (SELECT … FOR UPDATE) before reading
    // the token state. Concurrent requests for the same token block here until
    // the first caller commits. After the first caller revokes the token, all
    // subsequent callers find it revoked and are correctly rejected — eliminating
    // the TOCTOU race that allowed two concurrent refreshes to both succeed.
    RefreshToken stored;
    try {
      stored =
          refreshTokenRepository
              .findByTokenHashForUpdate(hash)
              .orElseThrow(
                  () ->
                      new BusinessException(
                          ErrorCode.UNAUTHORIZED, MessageConstants.INVALID_REFRESH_TOKEN));
    } catch (BusinessException e) {
      throw e;
    } catch (LockTimeoutException | CannotAcquireLockException ex) {
      log.warn(
          "[REFRESH] Lock timeout on token hash prefix={} — concurrent refresh in flight",
          hash.substring(0, Math.min(8, hash.length())));
      throw new BusinessException(
          ErrorCode.TOO_MANY_REQUESTS,
          MessageConstants.CONCURRENT_REFRESH_ALREADY_IN_PROGRESS_PLEASE_RETRY);
    }

    if (stored.isRevoked()) {
      // A revoked token in the critical section means:
      //  (a) an attacker is replaying a stolen token, OR
      //  (b) a concurrent request just beat this one to rotation.
      // Both cases trigger full session invalidation to protect the account.
      UUID compromisedUserId = stored.getUserId();
      log.warn(
          "[REFRESH] Token reuse detected userId={} — invalidating all sessions",
          compromisedUserId);
      userTokenInvalidationService.invalidateAllSessions(compromisedUserId);
      throw new BusinessException(
          ErrorCode.UNAUTHORIZED, MessageConstants.SESSION_EXPIRED_SIGN_IN_AGAIN);
    }

    if (stored.getExpiresAt().isBefore(Instant.now())) {
      throw new BusinessException(ErrorCode.UNAUTHORIZED, MessageConstants.REFRESH_TOKEN_EXPIRED);
    }

    if (!jwtTokenProvider.validateToken(request.getRefreshToken())) {
      throw new BusinessException(ErrorCode.UNAUTHORIZED, MessageConstants.INVALID_REFRESH_TOKEN);
    }

    Claims claims = jwtTokenProvider.parseClaims(request.getRefreshToken());
    if (jwtTokenProvider.getTokenType(claims) != TokenType.REFRESH) {
      throw new BusinessException(ErrorCode.UNAUTHORIZED, MessageConstants.INVALID_TOKEN_TYPE);
    }

    UUID userId = jwtTokenProvider.getUserId(claims);
    User user =
        userRepository
            .findById(userId)
            .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND));

    // Atomically revoke within the same transaction that holds the row lock.
    stored.setRevoked(true);
    refreshTokenRepository.save(stored);

    ParentProfile profile =
        parentProfileRepository
            .findByUser_Id(user.getId())
            .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND));

    return buildAuthResponse(user, profile);
  }

  private AuthResponse buildAuthResponse(User user, ParentProfile profile) {
    String accessToken =
        jwtTokenProvider.createAccessToken(user.getId(), user.getEmail(), user.getRole());
    String refreshToken =
        jwtTokenProvider.createRefreshToken(user.getId(), user.getEmail(), user.getRole());
    persistRefreshToken(user.getId(), refreshToken);

    return AuthResponse.builder()
        .accessToken(accessToken)
        .refreshToken(refreshToken)
        .tokenType("Bearer")
        .expiresInSeconds(jwtProperties.getAccessTokenExpirationMs() / 1000)
        .profile(toProfileResponse(user, profile))
        .build();
  }

  private void persistRefreshToken(UUID userId, String rawToken) {
    RefreshToken entity =
        RefreshToken.builder()
            .userId(userId)
            .tokenHash(hashToken(rawToken))
            .expiresAt(Instant.now().plusMillis(jwtProperties.getRefreshTokenExpirationMs()))
            .build();
    refreshTokenRepository.save(entity);
  }

  private ParentProfileResponse toProfileResponse(User user, ParentProfile profile) {
    return ParentProfileResponse.builder()
        .id(profile.getId())
        .firstName(profile.getFirstName())
        .lastName(profile.getLastName())
        .email(user.getEmail())
        .phone(profile.getPhone())
        .timezone(profile.getTimezone())
        .emailVerified(user.isEmailVerified())
        .build();
  }

  static String hashToken(String token) {
    try {
      MessageDigest digest = MessageDigest.getInstance("SHA-256");
      byte[] hash = digest.digest(token.getBytes(StandardCharsets.UTF_8));
      return HexFormat.of().formatHex(hash);
    } catch (NoSuchAlgorithmException e) {
      throw new IllegalStateException(e);
    }
  }

  private static String normalizeEmail(String email) {
    if (email == null) {
      return "";
    }
    return email.trim().toLowerCase();
  }
}
