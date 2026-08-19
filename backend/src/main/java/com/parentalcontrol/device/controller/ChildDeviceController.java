package com.parentalcontrol.device.controller;

import com.parentalcontrol.common.api.ApiResponse;
import com.parentalcontrol.common.constant.ApiConstants;
import com.parentalcontrol.device.dto.AckDeviceCommandRequest;
import com.parentalcontrol.device.dto.DevicePairRequest;
import com.parentalcontrol.device.dto.DevicePairResponse;
import com.parentalcontrol.device.dto.DeviceResponse;
import com.parentalcontrol.device.dto.HeartbeatRequest;
import com.parentalcontrol.device.dto.OfflinePinCredentialDto;
import com.parentalcontrol.device.dto.PendingDeviceCommandResponse;
import com.parentalcontrol.device.dto.RecordDeviceCommandExecutionRequest;
import com.parentalcontrol.device.dto.StreamFailureRequest;
import com.parentalcontrol.device.dto.TamperAttemptRequest;
import com.parentalcontrol.device.dto.TamperAttemptResponse;
import com.parentalcontrol.device.dto.TamperVerifyRequest;
import com.parentalcontrol.device.dto.TamperVerifyResponse;
import com.parentalcontrol.device.service.DeviceCommandService;
import com.parentalcontrol.device.service.DeviceService;
import com.parentalcontrol.device.service.TamperService;
import com.parentalcontrol.monitoring.service.StreamingService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping(ApiConstants.DEVICE)
@RequiredArgsConstructor
@Tag(name = "Device (Child)", description = "Child device pairing and heartbeat")
public class ChildDeviceController {

  private final DeviceService deviceService;
  private final DeviceCommandService deviceCommandService;
  private final StreamingService streamingService;
  private final TamperService tamperService;

  @PostMapping("/pair")
  @ResponseStatus(HttpStatus.CREATED)
  @Operation(summary = "Pair child device using parent pairing code")
  public ApiResponse<DevicePairResponse> pair(@Valid @RequestBody DevicePairRequest request) {
    return ApiResponse.ok("Device paired successfully", deviceService.pairDevice(request));
  }

  @PostMapping("/heartbeat")
  @Operation(summary = "Device heartbeat — updates online status and telemetry")
  public ApiResponse<DeviceResponse> heartbeat(@Valid @RequestBody HeartbeatRequest request) {
    return ApiResponse.ok(deviceService.heartbeat(request));
  }

  @GetMapping("/commands/pending")
  @Operation(summary = "Poll pending remote commands for this device")
  public ApiResponse<List<PendingDeviceCommandResponse>> pendingCommands() {
    return ApiResponse.ok(deviceCommandService.listPendingForDevice());
  }

  @GetMapping("/commands/in-flight")
  @Operation(summary = "List DELIVERED commands awaiting execution fingerprint (crash recovery)")
  public ApiResponse<List<PendingDeviceCommandResponse>> inFlightCommands() {
    return ApiResponse.ok(deviceCommandService.listInFlightDeliveredForDevice());
  }

  @PostMapping("/commands/{commandId}/claim")
  @Operation(summary = "Claim a pending command for delivery (idempotent execution guard)")
  public ApiResponse<Boolean> claimCommand(@PathVariable UUID commandId) {
    return ApiResponse.ok(deviceCommandService.tryClaimCommand(commandId));
  }

  @PostMapping("/commands/{commandId}/executed")
  @Operation(summary = "Record command execution fingerprint before ACK (idempotency guard)")
  public ApiResponse<Void> recordExecution(
      @PathVariable UUID commandId,
      @RequestBody(required = false) RecordDeviceCommandExecutionRequest request) {
    boolean success = request == null || request.isSuccess();
    String error = request != null ? request.getErrorMessage() : null;
    deviceCommandService.recordCommandExecution(commandId, success, error);
    return ApiResponse.okMessage("Command execution recorded");
  }

  @PostMapping("/commands/{commandId}/ack")
  @Operation(summary = "Acknowledge command execution on child device")
  public ApiResponse<Void> ackCommand(
      @PathVariable UUID commandId,
      @RequestBody(required = false) AckDeviceCommandRequest request) {
    boolean success = request == null || request.isSuccess();
    String error = request != null ? request.getErrorMessage() : null;
    deviceCommandService.acknowledgeCommand(commandId, success, error);
    return ApiResponse.okMessage("Command acknowledged");
  }

  @PostMapping("/stream-sessions/{sessionId}/failure")
  @Operation(summary = "Report child-side stream start/capture failure")
  public ApiResponse<Void> reportStreamFailure(
      @PathVariable UUID sessionId, @RequestBody(required = false) StreamFailureRequest request) {
    streamingService.reportStreamFailure(sessionId, request != null ? request.getReason() : null);
    return ApiResponse.okMessage("Stream failure recorded");
  }

  @PostMapping("/tamper/attempt")
  @Operation(
      summary = "Report tamper attempt (device admin or accessibility); issues verification code")
  public ApiResponse<TamperAttemptResponse> reportTamperAttempt(
      @RequestBody(required = false) TamperAttemptRequest request) {
    return ApiResponse.ok(tamperService.reportDisableAttempt(request));
  }

  @GetMapping("/tamper/offline-pin")
  @Operation(summary = "Fetch offline parent-password verifier for this device")
  public ApiResponse<OfflinePinCredentialDto> offlinePin() {
    return ApiResponse.ok(tamperService.getOfflinePinCredential());
  }

  @PostMapping("/tamper/verify")
  @Operation(summary = "Verify parent password, admin master password, or verification code")
  public ApiResponse<TamperVerifyResponse> verifyTamper(
      @Valid @RequestBody TamperVerifyRequest request) {
    return ApiResponse.ok(tamperService.verify(request));
  }

  @PostMapping("/tamper/admin-disabled")
  @Operation(summary = "Notify server that device administrator was deactivated")
  public ApiResponse<Void> reportAdminDisabled() {
    tamperService.reportAdminDisabled();
    return ApiResponse.okMessage("Device admin disabled recorded");
  }
}
