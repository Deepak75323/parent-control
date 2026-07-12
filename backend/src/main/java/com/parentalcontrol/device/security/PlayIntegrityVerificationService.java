package com.parentalcontrol.device.security;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.parentalcontrol.common.constant.AppConstants;
import com.parentalcontrol.common.constant.MessageConstants;
import com.parentalcontrol.common.exception.BusinessException;
import com.parentalcontrol.common.exception.ErrorCode;
import com.parentalcontrol.config.DeviceTrustProperties;
import com.parentalcontrol.config.PlayIntegrityProperties;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/** Decodes Play Integrity tokens via Google Play Integrity API. */
@Service
@RequiredArgsConstructor
@Slf4j
public class PlayIntegrityVerificationService {

  private static final String DECODE_URL = AppConstants.PLAY_INTEGRITY_DECODE_URL;

  private final PlayIntegrityProperties properties;
  private final DeviceTrustProperties deviceTrustProperties;
  private final com.parentalcontrol.device.service.PlayIntegrityOAuthTokenProvider tokenProvider;
  private final ObjectMapper objectMapper;
  private final HttpClient httpClient =
      HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();

  public IntegrityVerdict verifyAndDecode(String integrityToken, String expectedPackageName) {
    if (!properties.isRequired() && !deviceTrustProperties.isEnforcementEnabled()) {
      return null;
    }
    if (integrityToken == null || integrityToken.isBlank()) {
      if (properties.isRequired()) {
        throw new BusinessException(
            ErrorCode.UNAUTHORIZED, MessageConstants.PLAY_INTEGRITY_TOKEN_REQUIRED);
      }
      return null;
    }
    try {
      String accessToken = tokenProvider.getAccessToken();
      String url = String.format(DECODE_URL, properties.getPackageName());
      String requestBody =
          objectMapper.writeValueAsString(java.util.Map.of("integrityToken", integrityToken));
      HttpRequest request =
          HttpRequest.newBuilder()
              .uri(URI.create(url))
              .timeout(Duration.ofSeconds(15))
              .header("Authorization", "Bearer " + accessToken)
              .header("Content-Type", "application/json")
              .POST(HttpRequest.BodyPublishers.ofString(requestBody, StandardCharsets.UTF_8))
              .build();
      HttpResponse<String> response =
          httpClient.send(request, HttpResponse.BodyHandlers.ofString());
      if (response.statusCode() / 100 != 2) {
        log.warn(
            "Play Integrity decode failed status={} body={}",
            response.statusCode(),
            response.body());
        throw new BusinessException(
            ErrorCode.UNAUTHORIZED, MessageConstants.PLAY_INTEGRITY_VERIFICATION_FAILED);
      }
      return parseVerdict(response.body(), expectedPackageName);
    } catch (BusinessException e) {
      throw e;
    } catch (Exception e) {
      log.error("Play Integrity verification error", e);
      throw new BusinessException(
          ErrorCode.UNAUTHORIZED, MessageConstants.PLAY_INTEGRITY_VERIFICATION_FAILED);
    }
  }

  /**
   * @deprecated use {@link #verifyAndDecode(String, String)}
   */
  public void verifyIfRequired(String integrityToken, String expectedPackageName) {
    verifyAndDecode(integrityToken, expectedPackageName);
  }

  private IntegrityVerdict parseVerdict(String body, String expectedPackageName) throws Exception {
    JsonNode root = objectMapper.readTree(body);
    JsonNode external = root.path("tokenPayloadExternal");
    JsonNode appIntegrity = external.path("appIntegrity");
    JsonNode deviceIntegrity = external.path("deviceIntegrity");
    JsonNode accountDetails = external.path("accountDetails");
    JsonNode requestDetails = external.path("requestDetails");

    String packageName = appIntegrity.path("packageName").asText(null);
    if (packageName != null
        && expectedPackageName != null
        && !expectedPackageName.equals(packageName)) {
      throw new BusinessException(
          ErrorCode.UNAUTHORIZED, MessageConstants.PLAY_INTEGRITY_PACKAGE_MISMATCH);
    }

    String nonce = requestDetails.path("nonce").asText(null);
    validateNonce(nonce);

    List<String> deviceVerdicts = new ArrayList<>();
    JsonNode deviceVerdictNode = deviceIntegrity.path("deviceRecognitionVerdict");
    if (deviceVerdictNode.isArray()) {
      for (JsonNode node : deviceVerdictNode) {
        deviceVerdicts.add(node.asText());
      }
    }

    String appRecognition = appIntegrity.path("appRecognitionVerdict").asText("");
    boolean playRecognized = appRecognition.contains("PLAY_RECOGNIZED");
    if (properties.isRequirePlayRecognized() && !playRecognized) {
      throw new BusinessException(ErrorCode.UNAUTHORIZED, MessageConstants.APP_NOT_PLAY_RECOGNIZED);
    }

    return IntegrityVerdict.builder()
        .packageName(packageName)
        .appRecognitionVerdict(appRecognition)
        .deviceRecognitionVerdicts(deviceVerdicts)
        .appLicensingVerdict(accountDetails.path("appLicensingVerdict").asText(""))
        .requestNonce(nonce)
        .playRecognized(playRecognized)
        .meetsDeviceIntegrity(deviceVerdicts.contains("MEETS_DEVICE_INTEGRITY"))
        .meetsStrongIntegrity(deviceVerdicts.contains("MEETS_STRONG_INTEGRITY"))
        .meetsBasicIntegrity(deviceVerdicts.contains("MEETS_BASIC_INTEGRITY"))
        .build();
  }

  private void validateNonce(String nonce) {
    if (nonce == null || nonce.isBlank()) {
      return;
    }
    try {
      String decoded = new String(Base64.getDecoder().decode(nonce), StandardCharsets.UTF_8);
      String[] parts = decoded.split(":");
      if (parts.length < 3) {
        return;
      }
      long ts = Long.parseLong(parts[2]);
      long ageSeconds = Math.abs(System.currentTimeMillis() - ts) / 1000;
      if (ageSeconds > deviceTrustProperties.getNonceMaxAgeSeconds()) {
        throw new BusinessException(
            ErrorCode.UNAUTHORIZED, MessageConstants.PLAY_INTEGRITY_NONCE_EXPIRED);
      }
    } catch (BusinessException e) {
      throw e;
    } catch (Exception ignored) {
      // Non-standard nonce format — allow for dev tokens
    }
  }
}
