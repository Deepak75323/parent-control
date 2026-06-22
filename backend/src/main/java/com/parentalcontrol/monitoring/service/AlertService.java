package com.parentalcontrol.monitoring.service;

import com.parentalcontrol.common.api.PageResponse;
import com.parentalcontrol.common.exception.BusinessException;
import com.parentalcontrol.common.exception.ErrorCode;
import com.parentalcontrol.common.security.SecurityUtils;
import com.parentalcontrol.device.domain.Device;
import com.parentalcontrol.device.repository.DeviceRepository;
import com.parentalcontrol.monitoring.domain.Alert;
import com.parentalcontrol.monitoring.domain.AlertSeverity;
import com.parentalcontrol.monitoring.dto.AlertResponse;
import com.parentalcontrol.monitoring.repository.AlertRepository;
import com.parentalcontrol.user.domain.ParentProfile;
import com.parentalcontrol.user.repository.ParentProfileRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class AlertService {

    private static final int LOW_BATTERY_THRESHOLD = 20;

    private final AlertRepository alertRepository;
    private final DeviceRepository deviceRepository;
    private final ParentProfileRepository parentProfileRepository;
    private final RealtimeEventService realtimeEventService;

    @Transactional
    public void checkDeviceTrustDegraded(
            Device device,
            com.parentalcontrol.device.domain.DeviceTrustLevel previous,
            com.parentalcontrol.device.security.TrustEvaluation evaluation) {
        createAndPublish(
                device,
                "DEVICE_TRUST_DEGRADED",
                AlertSeverity.CRITICAL,
                "Device trust degraded",
                device.getDeviceName() + " trust level changed from " + previous + " to "
                        + evaluation.level() + " (score " + evaluation.score() + "). "
                        + "Review the device — it may be rooted, tampered, or running on an emulator.",
                Map.of(
                        "deviceId", device.getId().toString(),
                        "previousLevel", previous.name(),
                        "currentLevel", evaluation.level().name(),
                        "trustScore", evaluation.score(),
                        "reasons", evaluation.reasons()
                )
        );
    }

    @Transactional
    public void checkProjectionLost(Device device) {
        createAndPublish(
                device,
                "SCREEN_CAPTURE_LOST",
                AlertSeverity.WARNING,
                "Screen capture needs setup",
                device.getDeviceName() + " lost screen capture permission. "
                        + "Open the child app → Permissions → Grant screen capture again.",
                Map.of("deviceId", device.getId().toString())
        );
    }

    @Transactional
    public void checkAccessibilityServiceDisconnected(Device device) {
        createAndPublish(
                device,
                "ACCESSIBILITY_SERVICE_DISCONNECTED",
                AlertSeverity.WARNING,
                "Remote control reconnecting",
                device.getDeviceName() + " accessibility service disconnected briefly. "
                        + "Remote taps may be delayed until it reconnects.",
                Map.of("deviceId", device.getId().toString())
        );
    }

    @Transactional
    public void checkLowBattery(Device device, Integer batteryLevel) {
        if (batteryLevel == null || batteryLevel > LOW_BATTERY_THRESHOLD) {
            return;
        }
        createAndPublish(
                device,
                "LOW_BATTERY",
                AlertSeverity.WARNING,
                "Low battery",
                "Device battery is at " + batteryLevel + "%",
                Map.of("batteryLevel", batteryLevel)
        );
    }

    @Transactional
    public AlertResponse createAndPublish(
            Device device,
            String alertType,
            AlertSeverity severity,
            String title,
            String message,
            Map<String, Object> payload) {
        Alert alert = Alert.builder()
                .deviceId(device.getId())
                .alertType(alertType)
                .severity(severity)
                .title(title)
                .message(message)
                .payloadJson(payload)
                .build();
        alert = alertRepository.save(alert);

        AlertResponse response = toResponse(alert);
        UUID parentUserId = parentProfileRepository.findUserIdByProfileId(device.getParentProfileId())
                .orElse(null);
        if (parentUserId != null) {
            realtimeEventService.publishParentAlert(parentUserId, response);
        }
        return response;
    }

    @Transactional(readOnly = true)
    public PageResponse<AlertResponse> listForParent(int page, int size) {
        UUID userId = SecurityUtils.requireUserId();
        ParentProfile parent = parentProfileRepository.findByUser_Id(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND));

        var deviceIds = deviceRepository.findByParentProfileIdOrderByCreatedAtDesc(parent.getId())
                .stream()
                .map(Device::getId)
                .toList();

        if (deviceIds.isEmpty()) {
            return PageResponse.of(List.of(), page, size, 0);
        }

        Page<Alert> result = alertRepository.findByDeviceIdInOrderByCreatedAtDesc(
                deviceIds,
                PageRequest.of(page, Math.min(size, 100))
        );

        return PageResponse.of(
                result.getContent().stream().map(this::toResponse).toList(),
                page,
                result.getSize(),
                result.getTotalElements()
        );
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
