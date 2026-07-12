package com.parentalcontrol.common.security;

import com.parentalcontrol.common.constant.MessageConstants;
import com.parentalcontrol.common.exception.BusinessException;
import com.parentalcontrol.common.exception.ErrorCode;
import com.parentalcontrol.config.RateLimitProperties;
import java.time.Duration;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.env.Environment;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

@Service
public class RateLimitService {

  private final StringRedisTemplate redisTemplate;
  private final RateLimitProperties properties;
  private final Environment environment;

  public RateLimitService(
      @Autowired(required = false) StringRedisTemplate redisTemplate,
      RateLimitProperties properties,
      Environment environment) {
    this.redisTemplate = redisTemplate;
    this.properties = properties;
    this.environment = environment;
  }

  public void checkAuth(String clientKey) {
    check(
        "ratelimit:auth:" + clientKey,
        properties.getAuthRequestsPerMinute(),
        Duration.ofMinutes(1));
  }

  /** P2-07: per-account login rate limit (email), in addition to IP and lockout. */
  public void checkAuthEmail(String normalizedEmail) {
    if (normalizedEmail == null || normalizedEmail.isBlank()) {
      return;
    }
    check(
        "ratelimit:auth-email:" + normalizedEmail.toLowerCase(),
        properties.getAuthEmailRequestsPerMinute(),
        Duration.ofMinutes(1));
  }

  public void checkPairing(String clientKey) {
    check(
        "ratelimit:pair:" + clientKey,
        properties.getAuthRequestsPerMinute(),
        Duration.ofMinutes(1));
  }

  public void checkOnboarding(String clientKey) {
    check(
        "ratelimit:onboarding:" + clientKey,
        properties.getAuthRequestsPerMinute(),
        Duration.ofMinutes(1));
  }

  public void checkPasswordReset(String clientKey) {
    check(
        "ratelimit:pwdreset:" + clientKey,
        properties.getPasswordResetPerHour(),
        Duration.ofHours(1));
  }

  public void checkDeviceIngest(String deviceId) {
    check(
        "ratelimit:device:" + deviceId,
        properties.getDeviceIngestRequestsPerMinute(),
        Duration.ofMinutes(1));
  }

  public void checkTamperVerify(String deviceId) {
    check(
        "ratelimit:tamper:" + deviceId,
        properties.getTamperVerifyPerMinute(),
        Duration.ofMinutes(1));
  }

  public void checkTamperAttempt(String deviceId) {
    check(
        "ratelimit:tamper-attempt:" + deviceId,
        properties.getTamperAttemptPerMinute(),
        Duration.ofMinutes(1));
  }

  public void checkWebhook(String clientKey) {
    check(
        "ratelimit:webhook:" + clientKey,
        properties.getWebhookRequestsPerMinute(),
        Duration.ofMinutes(1));
  }

  public void checkPairingCodeCreation(String clientKey) {
    check(
        "ratelimit:pairing-code:" + clientKey,
        properties.getPairingCodeCreationPerMinute(),
        Duration.ofMinutes(1));
  }

  private void check(String key, int max, Duration window) {
    if (!properties.isEnabled()) {
      return;
    }
    if (redisTemplate == null) {
      if (properties.isFailClosed()) {
        throw new BusinessException(
            ErrorCode.TOO_MANY_REQUESTS, MessageConstants.SERVICE_TEMPORARILY_UNAVAILABLE);
      }
      return;
    }
    try {
      Long count = redisTemplate.opsForValue().increment(key);
      if (count != null && count == 1L) {
        redisTemplate.expire(key, window);
      }
      if (count != null && count > max) {
        throw new BusinessException(
            ErrorCode.TOO_MANY_REQUESTS, MessageConstants.RATE_LIMIT_EXCEEDED);
      }
    } catch (BusinessException e) {
      throw e;
    } catch (Exception ex) {
      if (properties.isFailClosed()) {
        throw new BusinessException(
            ErrorCode.TOO_MANY_REQUESTS, MessageConstants.SERVICE_TEMPORARILY_UNAVAILABLE);
      }
    }
  }

  public boolean isRedisRequired() {
    return properties.isEnabled() && properties.isFailClosed();
  }

  public boolean isRedisConnected() {
    if (redisTemplate == null) {
      return false;
    }
    try {
      redisTemplate.getConnectionFactory().getConnection().ping();
      return true;
    } catch (Exception ex) {
      return false;
    }
  }
}
