package com.parentalcontrol.device.service;

import com.parentalcontrol.common.constant.AppConstants;
import com.parentalcontrol.config.FcmProperties;
import com.parentalcontrol.device.domain.Device;
import java.util.HashMap;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

/**
 * Sends high-priority FCM data messages to wake the child app when STOMP/poll may be delayed. Uses
 * FCM HTTP v1 when {@code app.fcm.service-account-file} is set; otherwise legacy server key.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class FcmWakeService {

  private static final String FCM_LEGACY_URL = AppConstants.FCM_LEGACY_SEND_URL;

  private final FcmProperties fcmProperties;
  private final FcmOAuthTokenProvider fcmOAuthTokenProvider;
  private final RestClient restClient = RestClient.create();

  public void wakeDevice(Device device, String action, Map<String, String> data) {
    if (!fcmProperties.isEnabled()) {
      log.debug("FCM wake skipped — disabled");
      return;
    }
    String token = device.getFcmToken();
    if (token == null || token.isBlank()) {
      log.warn(
          "[TRACE_ID={}] FCM wake skipped — no token for device {}",
          data != null ? data.get("traceId") : "none",
          device.getId());
      return;
    }
    Map<String, String> payload = new HashMap<>(data != null ? data : Map.of());
    payload.put("action", action);
    payload.put("deviceId", device.getId().toString());
    String traceId = payload.get("traceId");
    log.info(
        "[TRACE_ID={}] FCM wake preparing action={} device={} payloadKeys={}",
        traceId != null ? traceId : "none",
        action,
        device.getId(),
        payload.keySet());

    try {
      if (fcmOAuthTokenProvider.isConfigured()) {
        sendV1(token, payload);
      } else if (fcmProperties.getServerKey() != null && !fcmProperties.getServerKey().isBlank()) {
        sendLegacy(token, payload);
      } else {
        log.warn(
            "[TRACE_ID={}] FCM enabled but neither service-account-file nor server-key is configured",
            traceId != null ? traceId : "none");
        return;
      }
      log.info(
          "[TRACE_ID={}] FCM wake sent successfully action={} device={}",
          traceId != null ? traceId : "none",
          action,
          device.getId());
    } catch (Exception e) {
      log.error(
          "[TRACE_ID={}] FCM wake failed for device {} action={}",
          traceId != null ? traceId : "none",
          device.getId(),
          action,
          e);
    }
  }

  private void sendV1(String deviceToken, Map<String, String> data) throws Exception {
    String projectId = fcmOAuthTokenProvider.getProjectId();
    String accessToken = fcmOAuthTokenProvider.getAccessToken();
    Map<String, Object> android = Map.of("priority", "HIGH");
    Map<String, Object> message = new HashMap<>();
    message.put("token", deviceToken);
    message.put("data", data);
    message.put("android", android);
    Map<String, Object> body = Map.of("message", message);

    restClient
        .post()
        .uri("https://fcm.googleapis.com/v1/projects/{projectId}/messages:send", projectId)
        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
        .contentType(MediaType.APPLICATION_JSON)
        .body(body)
        .retrieve()
        .toBodilessEntity();
  }

  private void sendLegacy(String deviceToken, Map<String, String> data) {
    Map<String, Object> body = new HashMap<>();
    body.put("to", deviceToken);
    body.put("priority", "high");
    body.put("data", data);

    restClient
        .post()
        .uri(FCM_LEGACY_URL)
        .header(HttpHeaders.AUTHORIZATION, "key=" + fcmProperties.getServerKey())
        .contentType(MediaType.APPLICATION_JSON)
        .body(body)
        .retrieve()
        .toBodilessEntity();
  }
}
