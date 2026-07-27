package com.parentalcontrol.device.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.parentalcontrol.audit.service.AuditService;
import com.parentalcontrol.billing.service.FeatureGateService;
import com.parentalcontrol.common.constant.AuditActionConstants;
import com.parentalcontrol.common.constant.MessageConstants;
import com.parentalcontrol.common.exception.BusinessException;
import com.parentalcontrol.common.exception.ErrorCode;
import com.parentalcontrol.common.security.SecurityUtils;
import com.parentalcontrol.config.DeviceCommandProperties;
import com.parentalcontrol.config.SecurityProperties;
import com.parentalcontrol.device.dao.DeviceCommandDao;
import com.parentalcontrol.device.dao.DeviceDao;
import com.parentalcontrol.device.domain.Device;
import com.parentalcontrol.device.domain.DeviceCommand;
import com.parentalcontrol.device.domain.DeviceCommandStatus;
import com.parentalcontrol.device.domain.DeviceCommandType;
import com.parentalcontrol.device.domain.DeviceStatus;
import com.parentalcontrol.device.dto.DeviceCommandRequest;
import com.parentalcontrol.device.dto.DeviceCommandResponse;
import com.parentalcontrol.device.dto.PendingDeviceCommandResponse;
import com.parentalcontrol.device.event.DeviceCommandCreatedEvent;
import com.parentalcontrol.observability.CommandObservabilityService;
import com.parentalcontrol.observability.CorrelationContext;
import com.parentalcontrol.user.dao.ParentProfileDao;
import com.parentalcontrol.user.domain.ParentProfile;
import com.parentalcontrol.user.service.ParentPinService;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Slf4j
public class DeviceCommandService {

  private final DeviceCommandDao deviceCommandRepository;
  private final DeviceDao deviceRepository;
  private final ParentProfileDao parentProfileRepository;
  private final AuditService auditService;
  private final ApplicationEventPublisher applicationEventPublisher;
  private final DeviceCommandProperties deviceCommandProperties;
  private final SecurityProperties securityProperties;
  private final ParentPinService parentPinService;
  private final ObjectMapper objectMapper;
  private final CommandObservabilityService commandObservabilityService;
  private final FeatureGateService featureGateService;

  @Transactional
  public DeviceCommandResponse sendCommand(UUID deviceId, DeviceCommandRequest request) {
    Device device = requireOwnedDevice(deviceId);
    featureGateService.requireActiveSubscription(device.getParentProfileId());
    validateCommand(device, request.getCommandType());
    requireStepUpIfSensitive(request);

    String payloadJson = toJson(request.getPayload());
    DeviceCommand command =
        deviceCommandRepository.save(
            DeviceCommand.builder()
                .deviceId(device.getId())
                .commandType(request.getCommandType())
                .payload(payloadJson)
                .status(DeviceCommandStatus.PENDING)
                .build());

    CorrelationContext.setCommandId(command.getId());
    commandObservabilityService.recordSent(request.getCommandType());

    applicationEventPublisher.publishEvent(
        new DeviceCommandCreatedEvent(
            device.getId(), command.getId(), command.getCommandType().name()));

    auditService.log(
        AuditActionConstants.DEVICE_COMMAND_ + request.getCommandType().name(),
        "device:" + deviceId,
        deviceId,
        Map.of("commandId", command.getId().toString()));

    return toResponse(command);
  }

  /**
   * Enqueue a non-interactive sync command and FCM-wake the device (used when parent policies
   * change). Skips parent step-up / subscription checks that apply to parent-initiated commands.
   */
  @Transactional
  public void enqueueSystemCommand(UUID deviceId, DeviceCommandType type) {
    Device device =
        deviceRepository
            .findById(deviceId)
            .orElseThrow(
                () ->
                    new BusinessException(
                        ErrorCode.NOT_FOUND, MessageConstants.DEVICE_NOT_FOUND));
    DeviceCommand command =
        deviceCommandRepository.save(
            DeviceCommand.builder()
                .deviceId(device.getId())
                .commandType(type)
                .payload(null)
                .status(DeviceCommandStatus.PENDING)
                .build());
    CorrelationContext.setCommandId(command.getId());
    commandObservabilityService.recordSent(type);
    applicationEventPublisher.publishEvent(
        new DeviceCommandCreatedEvent(device.getId(), command.getId(), type.name()));
    log.info("Enqueued system command type={} deviceId={} commandId={}", type, deviceId, command.getId());
  }

  @Transactional
  public List<PendingDeviceCommandResponse> listPendingForDevice() {
    UUID deviceId = SecurityUtils.requireDeviceId();
    reclaimStaleDeliveredCommands(deviceId);
    return deviceCommandRepository
        .findByDeviceIdAndStatusOrderByCreatedAtAsc(deviceId, DeviceCommandStatus.PENDING)
        .stream()
        .map(
            cmd ->
                PendingDeviceCommandResponse.builder()
                    .id(cmd.getId())
                    .commandType(cmd.getCommandType())
                    .payload(cmd.getPayload())
                    .build())
        .toList();
  }

  @Transactional(readOnly = true)
  public List<PendingDeviceCommandResponse> listInFlightDeliveredForDevice() {
    UUID deviceId = SecurityUtils.requireDeviceId();
    return deviceCommandRepository
        .findByDeviceIdAndStatusAndExecutedAtIsNullOrderByDeliveredAtAsc(
            deviceId, DeviceCommandStatus.DELIVERED)
        .stream()
        .map(
            cmd ->
                PendingDeviceCommandResponse.builder()
                    .id(cmd.getId())
                    .commandType(cmd.getCommandType())
                    .payload(cmd.getPayload())
                    .build())
        .toList();
  }

  /**
   * @return true when this device claimed the command for execution
   */
  @Transactional
  public boolean tryClaimCommand(UUID commandId) {
    UUID deviceId = SecurityUtils.requireDeviceId();
    DeviceCommand command =
        deviceCommandRepository
            .findById(commandId)
            .orElseThrow(
                () ->
                    new BusinessException(ErrorCode.NOT_FOUND, MessageConstants.COMMAND_NOT_FOUND));

    if (!command.getDeviceId().equals(deviceId)) {
      throw new BusinessException(ErrorCode.FORBIDDEN);
    }
    int claimed =
        deviceCommandRepository.claimPendingCommand(
            commandId, deviceId, Instant.now(), deviceCommandProperties.getMaxDeliveryAttempts());
    if (claimed > 0) {
      commandObservabilityService.recordDelivered(command.getCommandType());
    }
    return claimed > 0;
  }

  @Transactional
  public void recordCommandExecution(UUID commandId, boolean success, String errorMessage) {
    UUID deviceId = SecurityUtils.requireDeviceId();
    DeviceCommand command =
        deviceCommandRepository
            .findById(commandId)
            .orElseThrow(
                () ->
                    new BusinessException(ErrorCode.NOT_FOUND, MessageConstants.COMMAND_NOT_FOUND));

    if (!command.getDeviceId().equals(deviceId)) {
      throw new BusinessException(ErrorCode.FORBIDDEN);
    }

    if (command.getExecutedAt() != null) {
      log.debug(
          "Execution fingerprint already present commandId={} deviceId={} executedAt={}",
          commandId,
          deviceId,
          command.getExecutedAt());
      return;
    }

    if (command.getStatus() != DeviceCommandStatus.DELIVERED) {
      log.warn(
          "Ignoring execution record for command {} in status {} — expected DELIVERED",
          commandId,
          command.getStatus());
      return;
    }

    Instant now = Instant.now();
    String trimmedError = trimError(errorMessage);
    int recorded =
        deviceCommandRepository.recordExecutionFingerprint(
            commandId, deviceId, now, success, trimmedError);

    if (recorded > 0) {
      commandObservabilityService.recordExecuted(command.getCommandType(), success);
      log.info(
          "Recorded command execution fingerprint commandId={} deviceId={} success={} error={}",
          commandId,
          deviceId,
          success,
          trimmedError);
    } else {
      log.debug(
          "Execution fingerprint race lost for commandId={} deviceId={} — treating as idempotent",
          commandId,
          deviceId);
    }
  }

  @Transactional
  public void acknowledgeCommand(UUID commandId, boolean success, String errorMessage) {
    UUID deviceId = SecurityUtils.requireDeviceId();
    acknowledgeCommandForDevice(commandId, deviceId, success, errorMessage, false);
  }

  private void acknowledgeCommandForDevice(
      UUID commandId,
      UUID deviceId,
      boolean success,
      String errorMessage,
      boolean fromAutoFinalize) {
    DeviceCommand command =
        deviceCommandRepository
            .findById(commandId)
            .orElseThrow(
                () ->
                    new BusinessException(ErrorCode.NOT_FOUND, MessageConstants.COMMAND_NOT_FOUND));

    if (!command.getDeviceId().equals(deviceId)) {
      throw new BusinessException(ErrorCode.FORBIDDEN);
    }

    Instant now = Instant.now();
    int updated =
        success
            ? deviceCommandRepository.completeDeliveredCommand(commandId, deviceId, now)
            : deviceCommandRepository.failDeliveredCommand(
                commandId, deviceId, now, trimError(errorMessage));

    if (updated == 0) {
      DeviceCommand current = deviceCommandRepository.findById(commandId).orElseThrow();
      if (current.getStatus() == DeviceCommandStatus.COMPLETED
          || current.getStatus() == DeviceCommandStatus.FAILED) {
        log.debug(
            "Idempotent ack for command {} already in terminal status {}",
            commandId,
            current.getStatus());
        return;
      }
      log.warn(
          "Ignoring ack for command {} in status {} — delivery may have been requeued or superseded",
          commandId,
          current.getStatus());
      return;
    }

    command = deviceCommandRepository.findById(commandId).orElseThrow();
    commandObservabilityService.recordAcknowledged(command, success);
    if (fromAutoFinalize) {
      log.info(
          "Auto-finalized command {} for device {} success={} from execution fingerprint",
          commandId,
          deviceId,
          success);
    } else {
      log.info("Acknowledged command {} for device {} success={}", commandId, deviceId, success);
    }
    applyCommandSideEffects(deviceId, command, success);
  }

  private void applyCommandSideEffects(UUID deviceId, DeviceCommand command, boolean success) {
    if (!success) {
      return;
    }
    if (command.getCommandType() == DeviceCommandType.ENABLE_KIOSK) {
      updateKioskFlag(deviceId, true);
    } else if (command.getCommandType() == DeviceCommandType.DISABLE_KIOSK) {
      updateKioskFlag(deviceId, false);
    } else if (command.getCommandType() == DeviceCommandType.REVOKE_MONITORING) {
      deactivateDevice(deviceId);
    }
  }

  private void deactivateDevice(UUID deviceId) {
    deviceRepository
        .findById(deviceId)
        .ifPresent(
            device -> {
              device.setStatus(DeviceStatus.REVOKED);
              device.setKioskModeActive(false);
              device.setTokenVersion(device.getTokenVersion() + 1);
              deviceRepository.save(device);
              // Seat is freed for the plan: FeatureGateService counts non-revoked only.
              log.info(
                  "Device {} deactivated after revoke monitoring (tokenVersion={}) — plan seat freed",
                  deviceId,
                  device.getTokenVersion());
            });
  }

  private void reclaimStaleDeliveredCommands(UUID deviceId) {
    Instant now = Instant.now();
    Instant deliveredCutoff = now.minusSeconds(deviceCommandProperties.getStaleDeliveredSeconds());
    int maxAttempts = deviceCommandProperties.getMaxDeliveryAttempts();

    int requeued =
        deviceCommandRepository.requeueStaleDelivered(deviceId, deliveredCutoff, maxAttempts);
    if (requeued > 0) {
      log.warn(
          "Requeued {} stale DELIVERED command(s) without execution fingerprint to PENDING for device {}",
          requeued,
          deviceId);
    }

    int failed =
        deviceCommandRepository.failExhaustedStaleDelivered(
            deviceId, deliveredCutoff, maxAttempts, now);
    if (failed > 0) {
      log.warn(
          "Marked {} stale DELIVERED command(s) FAILED after {} delivery attempts for device {}",
          failed,
          maxAttempts,
          deviceId);
    }

    finalizeStaleExecutedCommands(deviceId, now);
  }

  /**
   * Commands with an execution fingerprint are never requeued. If ACK never arrives, auto-complete
   * from the recorded execution outcome after a longer timeout.
   */
  private void finalizeStaleExecutedCommands(UUID deviceId, Instant now) {
    Instant executedCutoff =
        now.minusSeconds(deviceCommandProperties.getStaleExecutedAwaitingAckSeconds());
    List<DeviceCommand> awaitingAck =
        deviceCommandRepository.findByDeviceIdAndStatusAndExecutedAtIsNotNullAndExecutedAtBefore(
            deviceId, DeviceCommandStatus.DELIVERED, executedCutoff);

    for (DeviceCommand command : awaitingAck) {
      boolean success = Boolean.TRUE.equals(command.getExecutionSuccess());
      acknowledgeCommandForDevice(
          command.getId(), deviceId, success, command.getExecutionError(), true);
    }
  }

  private static String trimError(String errorMessage) {
    if (errorMessage == null) {
      return null;
    }
    String trimmed = errorMessage.trim();
    return trimmed.isEmpty() ? null : trimmed;
  }

  private void updateKioskFlag(UUID deviceId, boolean active) {
    deviceRepository
        .findById(deviceId)
        .ifPresent(
            device -> {
              device.setKioskModeActive(active);
              deviceRepository.save(device);
            });
  }

  private void validateCommand(Device device, DeviceCommandType type) {
    if ((type == DeviceCommandType.ENABLE_KIOSK || type == DeviceCommandType.DISABLE_KIOSK)
        && !device.isDeviceOwnerActive()) {
      throw new BusinessException(
          ErrorCode.VALIDATION_ERROR,
          MessageConstants.KIOSK_MODE_REQUIRES_DEVICE_OWNER_ON_THE_CHILD_DEVICE);
    }
    if (isRemoteControlCommand(type) && !device.isAccessibilityEnabled()) {
      throw new BusinessException(
          ErrorCode.VALIDATION_ERROR,
          MessageConstants.REMOTE_CONTROL_REQUIRES_ACCESSIBILITY_TO_BE_ENABLED_ON_THE_CHILD_DEVICE
              + "Open the child app → Permissions → turn on Accessibility.");
    }
    if (isRemoteControlCommand(type) && device.getStatus() == DeviceStatus.REVOKED) {
      throw new BusinessException(
          ErrorCode.REPAIR_REQUIRED, MessageConstants.DEVICE_MONITORING_REVOKED_RE_PAIR_REQUIRED);
    }
    if ((type == DeviceCommandType.ENABLE_KIOSK
            || type == DeviceCommandType.DISABLE_KIOSK
            || type == DeviceCommandType.REVOKE_MONITORING)
        && device.getStatus() == DeviceStatus.REVOKED
        && type != DeviceCommandType.REVOKE_MONITORING) {
      throw new BusinessException(
          ErrorCode.REPAIR_REQUIRED, MessageConstants.DEVICE_MONITORING_REVOKED_RE_PAIR_REQUIRED);
    }
  }

  private static boolean isRemoteControlCommand(DeviceCommandType type) {
    return switch (type) {
      case TAP, SWIPE, GLOBAL_BACK, GLOBAL_HOME, TYPE_TEXT, LAUNCH_APP -> true;
      default -> false;
    };
  }

  /**
   * Commands that change the child device state in ways a stolen parent session should never be
   * able to trigger without re-proving the parent's identity. Includes all remote-control gestures
   * plus kiosk/revoke administrative actions.
   */
  private static boolean isSensitiveCommand(DeviceCommandType type) {
    return switch (type) {
      case TAP, SWIPE, TYPE_TEXT, LAUNCH_APP, ENABLE_KIOSK, DISABLE_KIOSK, REVOKE_MONITORING ->
          true;
      // GLOBAL_BACK / GLOBAL_HOME are low-risk navigation, no step-up needed.
      default -> false;
    };
  }

  private void requireStepUpIfSensitive(DeviceCommandRequest request) {
    if (!securityProperties.isRemoteControlPinRequired()) {
      return;
    }
    if (!isSensitiveCommand(request.getCommandType())) {
      return;
    }
    parentPinService.requireSensitiveActionAccess(
        request.getParentPin(), "remote control commands");
  }

  private Device requireOwnedDevice(UUID deviceId) {
    UUID userId = SecurityUtils.requireUserId();
    ParentProfile parent =
        parentProfileRepository
            .findByUser_Id(userId)
            .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND));

    Device device =
        deviceRepository
            .findById(deviceId)
            .orElseThrow(() -> new BusinessException(ErrorCode.DEVICE_NOT_FOUND));

    if (!device.getParentProfileId().equals(parent.getId())) {
      throw new BusinessException(ErrorCode.FORBIDDEN);
    }
    return device;
  }

  private String toJson(Map<String, Object> payload) {
    if (payload == null || payload.isEmpty()) {
      return null;
    }
    try {
      return objectMapper.writeValueAsString(payload);
    } catch (JsonProcessingException e) {
      return payload.toString();
    }
  }

  private DeviceCommandResponse toResponse(DeviceCommand command) {
    return DeviceCommandResponse.builder()
        .id(command.getId())
        .deviceId(command.getDeviceId())
        .commandType(command.getCommandType())
        .status(command.getStatus())
        .createdAt(command.getCreatedAt())
        .build();
  }
}
