package com.parentalcontrol.monitoring.service;

import com.parentalcontrol.billing.service.FeatureGateService;
import com.parentalcontrol.common.api.PageResponse;
import com.parentalcontrol.common.constant.TimeoutConstants;
import com.parentalcontrol.common.exception.BusinessException;
import com.parentalcontrol.common.exception.ErrorCode;
import com.parentalcontrol.common.security.SecurityUtils;
import com.parentalcontrol.device.dao.DeviceDao;
import com.parentalcontrol.device.domain.Device;
import com.parentalcontrol.monitoring.dao.AlertDao;
import com.parentalcontrol.monitoring.domain.Alert;
import com.parentalcontrol.monitoring.domain.AlertSeverity;
import com.parentalcontrol.monitoring.domain.AlertType;
import com.parentalcontrol.monitoring.dto.AlertResponse;
import com.parentalcontrol.user.dao.ParentProfileDao;
import com.parentalcontrol.user.domain.ParentProfile;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AlertService {

  private static final int LOW_BATTERY_THRESHOLD = TimeoutConstants.LOW_BATTERY_PERCENT_THRESHOLD;

  private final AlertDao alertRepository;
  private final DeviceDao deviceRepository;
  private final ParentProfileDao parentProfileRepository;
  private final RealtimeEventService realtimeEventService;
  private final FeatureGateService featureGateService;

  @Transactional
  public void checkDeviceTrustDegraded(
      Device device,
      com.parentalcontrol.device.domain.DeviceTrustLevel previous,
      com.parentalcontrol.device.security.TrustEvaluation evaluation) {
    createAndPublish(
        device,
        AlertType.DEVICE_TRUST_DEGRADED.code(),
        AlertSeverity.CRITICAL,
        "Device trust degraded",
        device.getDeviceName()
            + " trust level changed from "
            + previous
            + " to "
            + evaluation.level()
            + " (score "
            + evaluation.score()
            + "). "
            + "Review the device — it may be rooted, tampered, or running on an emulator.",
        Map.of(
            "deviceId", device.getId().toString(),
            "previousLevel", previous.name(),
            "currentLevel", evaluation.level().name(),
            "trustScore", evaluation.score(),
            "reasons", evaluation.reasons()));
  }

  @Transactional
  public void checkProjectionLost(Device device) {
    createAndPublish(
        device,
        AlertType.SCREEN_CAPTURE_LOST.code(),
        AlertSeverity.WARNING,
        "Screen capture needs setup",
        device.getDeviceName()
            + " lost screen capture permission. "
            + "Open the child app → Permissions → Grant screen capture again.",
        Map.of("deviceId", device.getId().toString()));
  }

  @Transactional
  public void checkAccessibilityServiceDisconnected(Device device) {
    createAndPublish(
        device,
        AlertType.ACCESSIBILITY_SERVICE_DISCONNECTED.code(),
        AlertSeverity.WARNING,
        "Remote control reconnecting",
        device.getDeviceName()
            + " accessibility service disconnected briefly. "
            + "Remote taps may be delayed until it reconnects.",
        Map.of("deviceId", device.getId().toString()));
  }

  @Transactional
  public void checkDeviceAdminDisableAttempt(
      Device device, String verificationCode, Instant codeExpiresAt) {
    createAndPublish(
        device,
        AlertType.DEVICE_ADMIN_DISABLE_ATTEMPT.code(),
        AlertSeverity.CRITICAL,
        "Device admin removal attempted",
        device.getDeviceName()
            + ": someone tried to deactivate device administrator. "
            + "Use the verification code below on the child device, or enter your parent PIN there.",
        Map.of(
            "deviceId", device.getId().toString(),
            "verificationCode", verificationCode,
            "codeExpiresAt", codeExpiresAt.toString()));
  }

  @Transactional
  public void checkChildLoginRecovery(
      Device device, String verificationCode, Instant codeExpiresAt) {
    createAndPublish(
        device,
        AlertType.CHILD_LOGIN_RECOVERY.code(),
        AlertSeverity.WARNING,
        "Child login recovery requested",
        device.getDeviceName()
            + " needs to log in. Enter the recovery code below on the child device "
            + "(Forgot password → Use setup code).",
        Map.of(
            "deviceId", device.getId().toString(),
            "verificationCode", verificationCode,
            "codeExpiresAt", codeExpiresAt.toString()));
  }

  @Transactional
  public void checkDeviceAdminDisabled(Device device) {
    createAndPublish(
        device,
        AlertType.DEVICE_ADMIN_DISABLED.code(),
        AlertSeverity.CRITICAL,
        "Device administrator removed",
        device.getDeviceName()
            + ": device administrator was deactivated. "
            + "Monitoring protection is reduced — open the child app to review permissions.",
        Map.of("deviceId", device.getId().toString()));
  }

  @Transactional
  public void checkLowBattery(Device device, Integer batteryLevel) {
    if (batteryLevel == null || batteryLevel > LOW_BATTERY_THRESHOLD) {
      return;
    }
    createAndPublish(
        device,
        AlertType.LOW_BATTERY.code(),
        AlertSeverity.WARNING,
        "Low battery",
        "Device battery is at " + batteryLevel + "%",
        Map.of("batteryLevel", batteryLevel));
  }

  @Transactional
  public AlertResponse createAndPublish(
      Device device,
      String alertType,
      AlertSeverity severity,
      String title,
      String message,
      Map<String, Object> payload) {
    Alert alert =
        Alert.builder()
            .deviceId(device.getId())
            .alertType(alertType)
            .severity(severity)
            .title(title)
            .message(message)
            .payloadJson(payload)
            .build();
    alert = alertRepository.save(alert);

    AlertResponse response = toResponse(alert);
    UUID parentUserId =
        parentProfileRepository.findUserIdByProfileId(device.getParentProfileId()).orElse(null);
    if (parentUserId != null) {
      realtimeEventService.publishParentAlert(parentUserId, response);
    }
    return response;
  }

  @Transactional(readOnly = true)
  public PageResponse<AlertResponse> listForParent(int page, int size) {
    UUID userId = SecurityUtils.requireUserId();
    ParentProfile parent =
        parentProfileRepository
            .findByUser_Id(userId)
            .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND));
    featureGateService.requireActiveSubscription(parent.getId());

    var deviceIds =
        deviceRepository.findByParentProfileIdOrderByCreatedAtDesc(parent.getId()).stream()
            .map(Device::getId)
            .toList();

    if (deviceIds.isEmpty()) {
      return PageResponse.of(List.of(), page, size, 0);
    }

    Page<Alert> result =
        alertRepository.findByDeviceIdInOrderByCreatedAtDesc(
            deviceIds, PageRequest.of(page, Math.min(size, 100)));

    return PageResponse.of(
        result.getContent().stream().map(this::toResponse).toList(),
        page,
        result.getSize(),
        result.getTotalElements());
  }

  private AlertResponse toResponse(Alert alert) {
    return AlertResponse.builder()
        .id(alert.getId())
        .deviceId(alert.getDeviceId())
        .alertType(alert.getAlertType())
        .severity(alert.getSeverity())
        .title(alert.getTitle())
        .message(alert.getMessage())
        .payloadJson(alert.getPayloadJson())
        .readAt(alert.getReadAt())
        .createdAt(alert.getCreatedAt())
        .build();
  }
}
