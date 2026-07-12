package com.parentalcontrol.policy.service;

import com.parentalcontrol.device.domain.Device;
import com.parentalcontrol.monitoring.domain.AlertSeverity;
import com.parentalcontrol.monitoring.service.AlertService;
import com.parentalcontrol.monitoring.service.DeviceAccessService;
import com.parentalcontrol.monitoring.service.RealtimeEventService;
import com.parentalcontrol.policy.dao.PolicyViolationEventDao;
import com.parentalcontrol.policy.domain.PolicyViolationEvent;
import com.parentalcontrol.policy.dto.PolicyViolationRequest;
import com.parentalcontrol.policy.dto.PolicyViolationResponse;
import com.parentalcontrol.user.dao.ParentProfileDao;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ViolationService {

  private final PolicyViolationEventDao violationRepository;
  private final DeviceAccessService deviceAccessService;
  private final AlertService alertService;
  private final RealtimeEventService realtimeEventService;
  private final ParentProfileDao parentProfileRepository;

  @Transactional
  public PolicyViolationResponse recordViolation(PolicyViolationRequest request) {
    Device device = deviceAccessService.requireAuthenticatedDevice();
    Instant occurredAt = request.getOccurredAt() != null ? request.getOccurredAt() : Instant.now();

    PolicyViolationEvent event =
        PolicyViolationEvent.builder()
            .deviceId(device.getId())
            .policyId(request.getPolicyId())
            .violationType(request.getViolationType())
            .packageName(request.getPackageName())
            .detailsJson(request.getDetails())
            .occurredAt(occurredAt)
            .build();
    event = violationRepository.save(event);

    Map<String, Object> payload = new HashMap<>();
    payload.put("violationType", request.getViolationType());
    if (request.getPolicyId() != null) {
      payload.put("policyId", request.getPolicyId().toString());
    }
    if (request.getPackageName() != null) {
      payload.put("packageName", request.getPackageName());
    }
    if (request.getDetails() != null) {
      payload.putAll(request.getDetails());
    }

    String title = violationTitle(request.getViolationType());
    var alert =
        alertService.createAndPublish(
            device,
            request.getViolationType(),
            AlertSeverity.WARNING,
            title,
            buildMessage(request),
            payload);

    parentProfileRepository
        .findUserIdByProfileId(device.getParentProfileId())
        .ifPresent(
            parentUserId ->
                realtimeEventService.publishPolicyViolation(
                    parentUserId, device.getId(), request.getViolationType(), alert.getId()));

    return PolicyViolationResponse.builder()
        .id(event.getId())
        .deviceId(device.getId())
        .policyId(event.getPolicyId())
        .violationType(event.getViolationType())
        .packageName(event.getPackageName())
        .details(event.getDetailsJson())
        .occurredAt(event.getOccurredAt())
        .build();
  }

  private String violationTitle(String type) {
    return switch (type) {
      case "APP_BLOCKED_ATTEMPT" -> "Blocked app opened";
      case "SCREEN_TIME_EXCEEDED" -> "Screen time limit exceeded";
      case "GEOFENCE_ENTER" -> "Geofence entered";
      case "GEOFENCE_EXIT" -> "Geofence exited";
      case "DEVICE_SCHEDULE_LOCK" -> "Device schedule active";
      default -> "Policy violation";
    };
  }

  private String buildMessage(PolicyViolationRequest request) {
    if (request.getPackageName() != null) {
      return request.getViolationType() + " — " + request.getPackageName();
    }
    return request.getViolationType();
  }
}
