package com.parentalcontrol.policy.service;

import com.parentalcontrol.common.constant.MessageConstants;
import com.parentalcontrol.common.exception.BusinessException;
import com.parentalcontrol.common.exception.ErrorCode;
import com.parentalcontrol.common.security.SecurityUtils;
import com.parentalcontrol.device.dao.DeviceDao;
import com.parentalcontrol.device.domain.Device;
import com.parentalcontrol.monitoring.domain.AlertSeverity;
import com.parentalcontrol.monitoring.service.AlertService;
import com.parentalcontrol.monitoring.service.DeviceAccessService;
import com.parentalcontrol.policy.dao.TimeExtensionRequestDao;
import com.parentalcontrol.policy.domain.TimeExtensionRequest;
import com.parentalcontrol.policy.domain.TimeExtensionStatus;
import com.parentalcontrol.policy.dto.CreateTimeExtensionRequest;
import com.parentalcontrol.policy.dto.RespondTimeExtensionRequest;
import com.parentalcontrol.policy.dto.TimeExtensionResponse;
import com.parentalcontrol.policy.dto.TimeGrantDto;
import com.parentalcontrol.user.dao.ParentProfileDao;
import com.parentalcontrol.user.domain.ParentProfile;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class TimeExtensionService {

  private final TimeExtensionRequestDao repository;
  private final DeviceAccessService deviceAccessService;
  private final DeviceDao deviceRepository;
  private final ParentProfileDao parentProfileRepository;
  private final AlertService alertService;

  @Transactional
  public TimeExtensionResponse createRequest(CreateTimeExtensionRequest request) {
    Device device = deviceAccessService.requireAuthenticatedDevice();
    expireStaleGrants(device.getId());

    TimeExtensionRequest entity =
        TimeExtensionRequest.builder()
            .deviceId(device.getId())
            .parentProfileId(device.getParentProfileId())
            .packageName(request.getPackageName())
            .appLabel(request.getAppLabel())
            .requestedMinutes(request.getRequestedMinutes())
            .status(TimeExtensionStatus.PENDING)
            .build();
    entity = repository.save(entity);

    String label = request.getAppLabel() != null ? request.getAppLabel() : request.getPackageName();
    alertService.createAndPublish(
        device,
        "TIME_EXTENSION_REQUEST",
        AlertSeverity.INFO,
        "More time requested",
        label + " — child asked for " + request.getRequestedMinutes() + " more minutes",
        Map.of(
            "requestId", entity.getId().toString(),
            "packageName", request.getPackageName(),
            "appLabel", label,
            "requestedMinutes", request.getRequestedMinutes()));

    return toResponse(entity, device.getDeviceName());
  }

  @Transactional(readOnly = true)
  public List<TimeExtensionResponse> listPendingForParent() {
    ParentProfile parent = requireParent();
    return repository
        .findByParentProfileIdAndStatusOrderByCreatedAtDesc(
            parent.getId(), TimeExtensionStatus.PENDING)
        .stream()
        .map(
            req -> {
              Device device = deviceRepository.findById(req.getDeviceId()).orElse(null);
              return toResponse(req, device != null ? device.getDeviceName() : "Device");
            })
        .toList();
  }

  @Transactional(readOnly = true)
  public List<TimeExtensionResponse> listForDevice(UUID deviceId) {
    deviceAccessService.requireOwnedDevice(deviceId);
    Device device = deviceRepository.findById(deviceId).orElseThrow();
    return repository.findByDeviceIdOrderByCreatedAtDesc(deviceId).stream()
        .limit(50)
        .map(req -> toResponse(req, device.getDeviceName()))
        .toList();
  }

  @Transactional
  public TimeExtensionResponse respond(UUID requestId, RespondTimeExtensionRequest request) {
    ParentProfile parent = requireParent();
    TimeExtensionRequest entity =
        repository
            .findById(requestId)
            .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND));
    if (!entity.getParentProfileId().equals(parent.getId())) {
      throw new BusinessException(ErrorCode.FORBIDDEN);
    }
    if (entity.getStatus() != TimeExtensionStatus.PENDING) {
      throw new BusinessException(ErrorCode.CONFLICT, MessageConstants.REQUEST_ALREADY_HANDLED);
    }

    Instant now = Instant.now();
    entity.setRespondedAt(now);
    if (request.isApproved()) {
      int grantMinutes =
          request.getGrantMinutes() > 0 ? request.getGrantMinutes() : entity.getRequestedMinutes();
      entity.setStatus(TimeExtensionStatus.APPROVED);
      entity.setGrantMinutes(grantMinutes);
      entity.setGrantedUntil(now.plusSeconds(grantMinutes * 60L));
    } else {
      entity.setStatus(TimeExtensionStatus.DENIED);
    }
    entity = repository.save(entity);

    Device device = deviceRepository.findById(entity.getDeviceId()).orElseThrow();
    return toResponse(entity, device.getDeviceName());
  }

  @Transactional(readOnly = true)
  public List<TimeGrantDto> activeGrantsForDevice(UUID deviceId) {
    expireStaleGrants(deviceId);
    Instant now = Instant.now();
    return repository
        .findByDeviceIdAndStatusAndGrantedUntilAfter(deviceId, TimeExtensionStatus.APPROVED, now)
        .stream()
        .map(
            req ->
                TimeGrantDto.builder()
                    .packageName(req.getPackageName())
                    .expiresAt(req.getGrantedUntil())
                    .build())
        .toList();
  }

  @Transactional
  public void expireStaleGrants(UUID deviceId) {
    Instant now = Instant.now();
    repository
        .findByDeviceIdAndStatusAndGrantedUntilAfter(
            deviceId, TimeExtensionStatus.APPROVED, Instant.EPOCH)
        .stream()
        .filter(req -> req.getGrantedUntil() != null && req.getGrantedUntil().isBefore(now))
        .forEach(req -> req.setStatus(TimeExtensionStatus.EXPIRED));
  }

  private ParentProfile requireParent() {
    UUID userId = SecurityUtils.requireUserId();
    return parentProfileRepository
        .findByUser_Id(userId)
        .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND));
  }

  private TimeExtensionResponse toResponse(TimeExtensionRequest req, String deviceName) {
    return TimeExtensionResponse.builder()
        .id(req.getId())
        .deviceId(req.getDeviceId())
        .deviceName(deviceName)
        .packageName(req.getPackageName())
        .appLabel(req.getAppLabel())
        .requestedMinutes(req.getRequestedMinutes())
        .status(req.getStatus())
        .grantMinutes(req.getGrantMinutes())
        .grantedUntil(req.getGrantedUntil())
        .createdAt(req.getCreatedAt())
        .respondedAt(req.getRespondedAt())
        .build();
  }
}
