package com.parentalcontrol.monitoring.service;

import com.parentalcontrol.device.domain.Device;
import com.parentalcontrol.messaging.ClusterStompMessagingService;
import com.parentalcontrol.monitoring.domain.AppUsageLog;
import com.parentalcontrol.monitoring.domain.LocationHistory;
import com.parentalcontrol.monitoring.domain.NotificationLog;
import com.parentalcontrol.monitoring.dto.AlertResponse;
import com.parentalcontrol.monitoring.dto.DeviceStatusEvent;
import com.parentalcontrol.monitoring.dto.LocationRealtimeEvent;
import com.parentalcontrol.monitoring.dto.NotificationRealtimeEvent;
import com.parentalcontrol.monitoring.dto.UsageStatsRealtimeEvent;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class RealtimeEventService {

  private final ClusterStompMessagingService messagingTemplate;
  private final ParentUserResolver parentUserResolver;

  public void publishDevicePaired(Device device) {
    publishDevicePaired(device, device.getDeviceName());
  }

  public void publishDevicePaired(Device device, String parentDisplayName) {
    publishDeviceStatus(device);
    parentUserResolver
        .resolveParentUserId(device)
        .ifPresent(
            parentUserId -> {
              var event =
                  Map.of(
                      "eventType", "DEVICE_PAIRED",
                      "deviceId", device.getId().toString(),
                      "deviceName", device.getDeviceName(),
                      "parentDisplayName", parentDisplayName != null ? parentDisplayName : "",
                      "status", device.getStatus().name());
              messagingTemplate.convertAndSendToUser(
                  parentUserId.toString(), "/queue/device-paired", event);
            });
  }

  /**
   * Notify the previous parent that a physical device was re-assigned to another family. Parent
   * apps should drop the device from their list without waiting for a manual refresh.
   */
  public void publishDeviceRemovedFromParent(
      UUID previousParentProfileId, UUID deviceId, String deviceName) {
    parentUserResolver
        .resolveUserIdByProfileId(previousParentProfileId)
        .ifPresent(
            parentUserId -> {
              var event =
                  Map.of(
                      "eventType",
                      "DEVICE_REASSIGNED",
                      "deviceId",
                      deviceId.toString(),
                      "deviceName",
                      deviceName != null ? deviceName : "",
                      "status",
                      "REMOVED");
              messagingTemplate.convertAndSendToUser(
                  parentUserId.toString(), "/queue/device-removed", event);
            });
  }

  public void publishDeviceStatus(Device device) {
    DeviceStatusEvent event =
        DeviceStatusEvent.builder()
            .deviceId(device.getId())
            .deviceName(device.getDeviceName())
            .status(device.getStatus())
            .batteryLevel(device.getBatteryLevel())
            .charging(device.getCharging())
            .networkType(device.getNetworkType())
            .latitude(device.getLastLatitude())
            .longitude(device.getLastLongitude())
            .foregroundApp(device.getForegroundApp())
            .lastSeenAt(device.getLastSeenAt())
            .mediaProjectionGranted(device.isMediaProjectionGranted())
            .mediaProjectionConsentState(device.getMediaProjectionConsentState())
            .silentScreenCapable(device.isSilentScreenCapable())
            .protectionLevel(device.getProtectionLevel())
            .monitoringDegraded(readDiagBool(device, "monitoringDegraded"))
            .monitoringMode(readDiagString(device, "monitoringMode"))
            .standbyBucket(readDiagString(device, "standbyBucket"))
            .accessibilityEnabled(device.isAccessibilityEnabled())
            .accessibilityServiceConnected(device.isAccessibilityServiceConnected())
            .permAccessibility(resolvePermAccessibility(device))
            .permBattery(readDiagBool(device, "batteryExempt"))
            .permAutostart(readDiagBool(device, "autostartPromptDone"))
            .permOverlay(readDiagBool(device, "overlayGranted"))
            .permCamera(readDiagBool(device, "cameraGranted"))
            .permMicrophone(readDiagBool(device, "microphoneGranted"))
            .permNotifications(readDiagBool(device, "notificationPermissionGranted"))
            .permFullScreenIntent(readDiagBool(device, "fullScreenIntentGranted"))
            .deviceReadinessScore(device.getDeviceReadinessScore())
            .readinessFailureTags(resolveReadinessFailureTags(device))
            .build();

    messagingTemplate.convertAndSend("/topic/device/" + device.getId() + "/status", event);
    parentUserResolver
        .resolveParentUserId(device)
        .ifPresent(
            parentUserId ->
                messagingTemplate.convertAndSendToUser(
                    parentUserId.toString(), "/queue/device-status", event));
  }

  private Boolean readDiagBool(Device device, String key) {
    Map<String, Object> diag = device.getOemDiagnosticsJson();
    if (diag == null) {
      return null;
    }
    Object raw = diag.get(key);
    if (raw instanceof Boolean b) {
      return b;
    }
    if (raw == null) {
      return null;
    }
    return "true".equalsIgnoreCase(String.valueOf(raw));
  }

  private String readDiagString(Device device, String key) {
    Map<String, Object> diag = device.getOemDiagnosticsJson();
    if (diag == null || diag.get(key) == null) {
      return null;
    }
    return String.valueOf(diag.get(key));
  }

  private Boolean resolvePermAccessibility(Device device) {
    List<String> tags = resolveReadinessFailureTags(device);
    if (tags != null && tags.contains("ACCESSIBILITY_DISABLED")) {
      return false;
    }
    Boolean enabledDiag = readDiagBool(device, "accessibilityEnabled");
    if (Boolean.TRUE.equals(enabledDiag) || device.isAccessibilityEnabled()) {
      return true;
    }
    if (Boolean.FALSE.equals(enabledDiag) || !device.isAccessibilityEnabled()) {
      return false;
    }
    Boolean connectedDiag = readDiagBool(device, "accessibilityServiceConnected");
    if (connectedDiag != null) {
      return Boolean.TRUE.equals(connectedDiag);
    }
    return device.isAccessibilityEnabled();
  }

  private List<String> resolveReadinessFailureTags(Device device) {
    if (device.getOemFailureClassifications() != null
        && !device.getOemFailureClassifications().isEmpty()) {
      return device.getOemFailureClassifications();
    }
    Map<String, Object> diag = device.getOemDiagnosticsJson();
    if (diag == null) {
      return null;
    }
    Object raw = diag.get("failureClassifications");
    if (raw instanceof List<?> list) {
      return list.stream().map(String::valueOf).toList();
    }
    return null;
  }

  public void publishLocation(Device device, LocationHistory entry) {
    LocationRealtimeEvent event =
        LocationRealtimeEvent.builder()
            .deviceId(device.getId())
            .id(entry.getId())
            .latitude(entry.getLatitude())
            .longitude(entry.getLongitude())
            .accuracyM(entry.getAccuracyM())
            .recordedAt(entry.getRecordedAt())
            .build();

    parentUserResolver
        .resolveParentUserId(device)
        .ifPresent(
            parentUserId ->
                messagingTemplate.convertAndSendToUser(
                    parentUserId.toString(), "/queue/locations", event));
  }

  public void publishNotification(Device device, NotificationLog log) {
    NotificationRealtimeEvent event =
        NotificationRealtimeEvent.builder()
            .deviceId(device.getId())
            .id(log.getId())
            .packageName(log.getPackageName())
            .title(log.getTitle())
            .body(log.getBody())
            .postedAt(log.getPostedAt())
            .build();

    parentUserResolver
        .resolveParentUserId(device)
        .ifPresent(
            parentUserId ->
                messagingTemplate.convertAndSendToUser(
                    parentUserId.toString(), "/queue/notifications", event));
  }

  public void publishUsageStats(Device device, AppUsageLog log) {
    UsageStatsRealtimeEvent event =
        UsageStatsRealtimeEvent.builder()
            .deviceId(device.getId())
            .id(log.getId())
            .packageName(log.getPackageName())
            .usageMs(log.getUsageMs())
            .recordedAt(log.getRecordedAt())
            .build();

    parentUserResolver
        .resolveParentUserId(device)
        .ifPresent(
            parentUserId ->
                messagingTemplate.convertAndSendToUser(
                    parentUserId.toString(), "/queue/usage-stats", event));
  }

  public void publishParentAlert(UUID parentUserId, AlertResponse alert) {
    messagingTemplate.convertAndSend("/topic/parent/" + parentUserId + "/alerts", alert);
    messagingTemplate.convertAndSendToUser(parentUserId.toString(), "/queue/alerts", alert);
  }

  public void publishPolicyUpdated(UUID parentUserId, UUID deviceId, String policyVersion) {
    var event =
        Map.of(
            "eventType",
            "POLICY_UPDATED",
            "deviceId",
            deviceId.toString(),
            "policyVersion",
            policyVersion);
    messagingTemplate.convertAndSendToUser(parentUserId.toString(), "/queue/policy-updates", event);
  }

  public void publishDevicePolicyUpdate(UUID deviceId, String policyVersion) {
    messagingTemplate.convertAndSend(
        "/topic/device/" + deviceId + "/policy-updates",
        Map.of("eventType", "POLICY_UPDATED", "policyVersion", policyVersion));
  }

  public void publishPolicyViolation(
      UUID parentUserId, UUID deviceId, String violationType, UUID alertId) {
    var event =
        Map.of(
            "eventType",
            "POLICY_VIOLATION",
            "deviceId",
            deviceId.toString(),
            "violationType",
            violationType,
            "alertId",
            alertId.toString());
    messagingTemplate.convertAndSendToUser(
        parentUserId.toString(), "/queue/policy-violations", event);
  }

  public void publishGeofenceEvent(
      UUID parentUserId, UUID deviceId, String transition, String geofenceName) {
    var event =
        Map.of(
            "eventType", transition,
            "deviceId", deviceId.toString(),
            "geofenceName", geofenceName);
    messagingTemplate.convertAndSendToUser(
        parentUserId.toString(), "/queue/geofence-events", event);
  }

  public void publishStreamingState(
      UUID parentUserId, UUID deviceId, String roomId, String status, String streamType) {
    publishStreamingState(parentUserId, deviceId, roomId, status, streamType, null, null);
  }

  public void publishStreamingState(
      UUID parentUserId,
      UUID deviceId,
      String roomId,
      String status,
      String streamType,
      String failureReason) {
    publishStreamingState(parentUserId, deviceId, roomId, status, streamType, failureReason, null);
  }

  public void publishStreamingState(
      UUID parentUserId,
      UUID deviceId,
      String roomId,
      String status,
      String streamType,
      String failureReason,
      String traceId) {
    var event = new java.util.HashMap<String, String>();
    event.put("eventType", "STREAMING_STATE");
    event.put("deviceId", deviceId.toString());
    event.put("roomId", roomId);
    event.put("status", status);
    event.put("streamType", streamType);
    if (failureReason != null && !failureReason.isBlank()) {
      event.put("failureReason", failureReason);
    }
    if (traceId != null && !traceId.isBlank()) {
      event.put("traceId", traceId);
    }
    messagingTemplate.convertAndSendToUser(
        parentUserId.toString(), "/queue/streaming-state", event);
  }

  public void publishSignalingMessage(String roomId, Map<String, Object> message) {
    messagingTemplate.convertAndSend("/topic/signaling/" + roomId, message);
  }

  public void publishDeviceStreamRequest(
      UUID deviceId, String roomId, String streamType, UUID sessionId) {
    publishDeviceStreamRequest(deviceId, roomId, streamType, sessionId, null, null);
  }

  public void publishDeviceStreamRequest(
      UUID deviceId, String roomId, String streamType, UUID sessionId, String cameraFacing) {
    publishDeviceStreamRequest(deviceId, roomId, streamType, sessionId, cameraFacing, null);
  }

  public void publishDeviceStreamRequest(
      UUID deviceId,
      String roomId,
      String streamType,
      UUID sessionId,
      String cameraFacing,
      String traceId) {
    var payload = new java.util.HashMap<String, String>();
    payload.put("eventType", "STREAM_REQUEST");
    payload.put("roomId", roomId);
    payload.put("streamType", streamType);
    payload.put("sessionId", sessionId.toString());
    if (cameraFacing != null && !cameraFacing.isBlank()) {
      payload.put("cameraFacing", cameraFacing);
    }
    if (traceId != null && !traceId.isBlank()) {
      payload.put("traceId", traceId);
    }
    messagingTemplate.convertAndSend("/topic/device/" + deviceId + "/stream-request", payload);
  }

  public void publishDeviceStreamStop(UUID deviceId, UUID sessionId, String roomId) {
    messagingTemplate.convertAndSend(
        "/topic/device/" + deviceId + "/stream-stop",
        Map.of(
            "eventType",
            "STREAM_STOP",
            "sessionId",
            sessionId.toString(),
            "roomId",
            roomId != null ? roomId : ""));
  }

  public void publishDeviceStreamControl(UUID deviceId, UUID sessionId, String command) {
    messagingTemplate.convertAndSend(
        "/topic/device/" + deviceId + "/stream-control",
        Map.of(
            "eventType", "STREAM_CONTROL", "sessionId", sessionId.toString(), "command", command));
  }

  public void publishDeviceCommand(UUID deviceId, UUID commandId, String commandType) {
    messagingTemplate.convertAndSend(
        "/topic/device/" + deviceId + "/commands",
        Map.of(
            "eventType",
            "DEVICE_COMMAND",
            "commandId",
            commandId.toString(),
            "commandType",
            commandType));
  }
}
