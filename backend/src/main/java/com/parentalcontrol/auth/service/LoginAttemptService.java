package com.parentalcontrol.auth.service;

import com.parentalcontrol.common.constant.CacheConstants;
import com.parentalcontrol.common.constant.MessageConstants;
import com.parentalcontrol.common.exception.BusinessException;
import com.parentalcontrol.common.exception.ErrorCode;
import com.parentalcontrol.common.util.SensitiveDataRedactor;
import com.parentalcontrol.config.AccountLockoutProperties;
import java.time.Duration;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

/**
 * Per-account brute-force protection backed by Redis.
 *
 * <p>Redis key layout:
 *
 * <ul>
 *   <li>{@code lockout:attempt:{email}} — atomic INCR counter with sliding-window TTL.
 *   <li>{@code lockout:locked:{email}} — presence signals a locked account; TTL = lock duration.
 * </ul>
 *
 * <p>All Redis operations used here are atomic (INCR, EXPIRE, SET EX, DEL, EXISTS), so concurrent
 * login requests do not require application-level locks.
 *
 * <p>Redis unavailability is handled gracefully: when {@code failClosed=false} (default) tracking
 * is skipped and login proceeds normally. The existing IP-based rate limit ({@link
 * com.parentalcontrol.common.security.RateLimitService}) remains active as a secondary defence
 * layer.
 */
@Service
@Slf4j
public class LoginAttemptService {

  static final String ATTEMPT_KEY_PREFIX = CacheConstants.LOCKOUT_ATTEMPT_PREFIX;
  static final String LOCKED_KEY_PREFIX = CacheConstants.LOCKOUT_LOCKED_PREFIX;

  private final AccountLockoutProperties properties;
  private final StringRedisTemplate redisTemplate;

  public LoginAttemptService(
      AccountLockoutProperties properties,
      @Autowired(required = false) StringRedisTemplate redisTemplate) {
    this.properties = properties;
    this.redisTemplate = redisTemplate;
  }

  /**
   * Checks whether the account identified by {@code normalizedEmail} is currently locked due to
   * repeated failed login attempts.
   *
   * <p>Throws {@link BusinessException}({@link ErrorCode#ACCOUNT_LOCKED}) when the account is
   * locked so the caller does not need a separate conditional check.
   */
  public void assertNotLocked(String normalizedEmail) {
    if (!properties.isEnabled()) {
      return;
    }
    if (redisTemplate == null) {
      handleRedisUnavailable("assertNotLocked", normalizedEmail);
      return;
    }
    try {
      Boolean locked = redisTemplate.hasKey(LOCKED_KEY_PREFIX + normalizedEmail);
      if (locked) {
        log.warn(
            "[LOCKOUT] Account locked email={} lockDurationSeconds={}",
            SensitiveDataRedactor.email(normalizedEmail),
            properties.getLockDurationSeconds());
        throw new BusinessException(
            ErrorCode.ACCOUNT_LOCKED,
            MessageConstants.ACCOUNT_TEMPORARILY_LOCKED_TOO_MANY_FAILED_ATTEMPTS
                + "Please try again later.");
      }
    } catch (BusinessException e) {
      throw e;
    } catch (Exception ex) {
      log.error(
          "[LOCKOUT] Redis error during assertNotLocked email={}",
          SensitiveDataRedactor.email(normalizedEmail),
          ex);
      handleRedisUnavailable("assertNotLocked", normalizedEmail);
    }
  }

  /**
   * Records a failed login attempt for the given account.
   *
   * <p>Uses Redis {@code INCR} (atomic) to safely handle concurrent attempts. On the first failure
   * within a window the sliding-window TTL is set. When the attempt count reaches {@code
   * maxAttempts} a separate locked key is written with a {@code lockDurationSeconds} TTL.
   *
   * <p>Errors are logged but never propagate — the caller already throws {@link
   * ErrorCode#INVALID_CREDENTIALS}.
   */
  public void recordFailure(String normalizedEmail) {
    if (!properties.isEnabled() || redisTemplate == null) {
      return;
    }
    try {
      String attemptKey = ATTEMPT_KEY_PREFIX + normalizedEmail;
      Long count = redisTemplate.opsForValue().increment(attemptKey);
      if (count != null && count == 1L) {
        // First failure in this window — start the sliding-window TTL.
        redisTemplate.expire(attemptKey, Duration.ofSeconds(properties.getAttemptWindowSeconds()));
      }

      log.warn(
          "[LOCKOUT] Failed login attempt={} maxAttempts={} email={}",
          count,
          properties.getMaxAttempts(),
          SensitiveDataRedactor.email(normalizedEmail));

      if (count != null && count >= properties.getMaxAttempts()) {
        String lockedKey = LOCKED_KEY_PREFIX + normalizedEmail;
        redisTemplate
            .opsForValue()
            .set(lockedKey, "1", Duration.ofSeconds(properties.getLockDurationSeconds()));
        log.warn(
            "[LOCKOUT] Account LOCKED email={} attempts={} lockDurationSeconds={}",
            SensitiveDataRedactor.email(normalizedEmail),
            count,
            properties.getLockDurationSeconds());
      }
    } catch (Exception ex) {
      log.error(
          "[LOCKOUT] Redis error during recordFailure email={}",
          SensitiveDataRedactor.email(normalizedEmail),
          ex);
      // fail-open: the IP-based rate limit remains active as a fallback
    }
  }

  /**
   * Clears all lockout state for the account on a successful login.
   *
   * <p>Removes both the attempt counter and the locked key. Errors are logged but never propagate —
   * the login already succeeded.
   */
  public void recordSuccess(String normalizedEmail) {
    if (!properties.isEnabled() || redisTemplate == null) {
      return;
    }
    try {
      Boolean hadAttempts = redisTemplate.delete(ATTEMPT_KEY_PREFIX + normalizedEmail);
      Boolean wasLocked = redisTemplate.delete(LOCKED_KEY_PREFIX + normalizedEmail);
      if (Boolean.TRUE.equals(hadAttempts) || Boolean.TRUE.equals(wasLocked)) {
        log.info(
            "[LOCKOUT] Cleared lockout state on successful login email={}",
            SensitiveDataRedactor.email(normalizedEmail));
      }
    } catch (Exception ex) {
      log.error(
          "[LOCKOUT] Redis error during recordSuccess email={}",
          SensitiveDataRedactor.email(normalizedEmail),
          ex);
    }
  }

  // -------------------------------------------------------------------------

  private void handleRedisUnavailable(String operation, String normalizedEmail) {
    if (properties.isFailClosed()) {
      log.warn(
          "[LOCKOUT] Redis unavailable — failing closed op={} email={}",
          operation,
          SensitiveDataRedactor.email(normalizedEmail));
      throw new BusinessException(
          ErrorCode.ACCOUNT_LOCKED,
          MessageConstants.AUTHENTICATION_SERVICE_TEMPORARILY_UNAVAILABLE_TRY_AGAIN_LATER);
    }
    // fail-open: log and continue so legitimate users are not blocked
    log.warn(
        "[LOCKOUT] Redis unavailable — skipping lockout check op={} email={}",
        operation,
        SensitiveDataRedactor.email(normalizedEmail));
  }
}
