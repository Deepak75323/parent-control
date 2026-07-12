package com.parentalcontrol.device.security;

import com.parentalcontrol.common.constant.AppConstants;
import com.parentalcontrol.common.constant.MessageConstants;
import com.parentalcontrol.common.exception.BusinessException;
import com.parentalcontrol.common.exception.ErrorCode;
import com.parentalcontrol.common.security.SecretEncryptionService;
import com.parentalcontrol.config.HmacProperties;
import com.parentalcontrol.device.dao.DeviceDao;
import com.parentalcontrol.device.domain.Device;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import java.util.UUID;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class DeviceHmacService {

  private static final String NONCE_PREFIX = AppConstants.HMAC_NONCE_KEY_PREFIX;

  private final DeviceDao deviceRepository;
  private final HmacProperties hmacProperties;
  private final StringRedisTemplate redisTemplate;
  private final SecretEncryptionService secretEncryptionService;
  private final SecureRandom secureRandom = new SecureRandom();

  public String generateSecret() {
    byte[] bytes = new byte[32];
    secureRandom.nextBytes(bytes);
    return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
  }

  public void verify(
      UUID deviceId,
      String method,
      String path,
      String timestampHeader,
      String nonce,
      String body,
      String signatureHeader) {
    if (!hmacProperties.isRequired()) {
      if (signatureHeader == null || signatureHeader.isBlank()) {
        return;
      }
    } else {
      requireHeaders(timestampHeader, nonce, signatureHeader);
    }

    Device device =
        deviceRepository
            .findById(deviceId)
            .orElseThrow(() -> new BusinessException(ErrorCode.DEVICE_NOT_FOUND));

    if (device.getDeviceHmacKey() == null || device.getDeviceHmacKey().isBlank()) {
      throw new BusinessException(
          ErrorCode.FORBIDDEN, MessageConstants.DEVICE_HMAC_NOT_CONFIGURED_RE_PAIR_DEVICE);
    }

    long timestamp;
    try {
      timestamp = Long.parseLong(timestampHeader);
    } catch (NumberFormatException e) {
      throw new BusinessException(
          ErrorCode.VALIDATION_ERROR, MessageConstants.INVALID_X_DEVICE_TIMESTAMP);
    }

    long skew = Math.abs(Instant.now().getEpochSecond() - timestamp);
    if (skew > hmacProperties.getMaxTimestampSkewSeconds()) {
      throw new BusinessException(
          ErrorCode.UNAUTHORIZED, MessageConstants.REQUEST_TIMESTAMP_OUTSIDE_ALLOWED_WINDOW);
    }

    if (isNonceReplay(deviceId, nonce)) {
      throw new BusinessException(ErrorCode.CONFLICT, MessageConstants.DUPLICATE_REQUEST_NONCE);
    }

    String bodyHash = sha256Hex(body == null ? "" : body);
    String canonical =
        method.toUpperCase() + "\n" + path + "\n" + timestampHeader + "\n" + bodyHash;
    String secret = secretEncryptionService.decrypt(device.getDeviceHmacKey());
    String expected = sign(secret, canonical);

    if (!constantTimeEquals(expected, signatureHeader.trim())) {
      throw new BusinessException(
          ErrorCode.UNAUTHORIZED, MessageConstants.INVALID_DEVICE_SIGNATURE);
    }
  }

  public String sign(String secret, String canonical) {
    try {
      Mac mac = Mac.getInstance("HmacSHA256");
      mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
      return Base64.getEncoder()
          .encodeToString(mac.doFinal(canonical.getBytes(StandardCharsets.UTF_8)));
    } catch (Exception e) {
      throw new BusinessException(ErrorCode.INTERNAL_ERROR, MessageConstants.HMAC_SIGNING_FAILED);
    }
  }

  private boolean isNonceReplay(UUID deviceId, String nonce) {
    if (nonce == null || nonce.isBlank()) {
      return false;
    }
    try {
      String key = NONCE_PREFIX + deviceId + ":" + nonce;
      Boolean inserted =
          redisTemplate
              .opsForValue()
              .setIfAbsent(
                  key, "1", Duration.ofSeconds(hmacProperties.getMaxTimestampSkewSeconds()));
      return inserted == null || !inserted;
    } catch (Exception e) {
      if (hmacProperties.isRequired()) {
        throw new BusinessException(
            ErrorCode.INTERNAL_ERROR, MessageConstants.NONCE_STORE_UNAVAILABLE);
      }
      return true;
    }
  }

  private static String sha256Hex(String input) {
    try {
      MessageDigest digest = MessageDigest.getInstance("SHA-256");
      return HexFormat.of().formatHex(digest.digest(input.getBytes(StandardCharsets.UTF_8)));
    } catch (Exception e) {
      throw new IllegalStateException(e);
    }
  }

  private static boolean constantTimeEquals(String a, String b) {
    if (a == null || b == null || a.length() != b.length()) {
      return false;
    }
    int result = 0;
    for (int i = 0; i < a.length(); i++) {
      result |= a.charAt(i) ^ b.charAt(i);
    }
    return result == 0;
  }

  private static void requireHeaders(String timestamp, String nonce, String signature) {
    if (timestamp == null
        || timestamp.isBlank()
        || nonce == null
        || nonce.isBlank()
        || signature == null
        || signature.isBlank()) {
      throw new BusinessException(ErrorCode.UNAUTHORIZED, MessageConstants.MISSING_HMAC_HEADERS);
    }
  }
}
