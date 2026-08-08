package com.parentalcontrol.device.controller;

import com.parentalcontrol.common.api.ApiResponse;
import com.parentalcontrol.common.constant.ApiConstants;
import com.parentalcontrol.device.dto.CreatePairingCodeRequest;
import com.parentalcontrol.device.dto.DeviceCommandRequest;
import com.parentalcontrol.device.dto.DeviceCommandResponse;
import com.parentalcontrol.device.dto.DeviceResponse;
import com.parentalcontrol.device.dto.DeviceWakeResponse;
import com.parentalcontrol.device.dto.PairingCodeResponse;
import com.parentalcontrol.device.dto.PairingCodeStatusResponse;
import com.parentalcontrol.device.dto.TamperCodeResponse;
import com.parentalcontrol.device.service.DeviceCommandService;
import com.parentalcontrol.device.service.DeviceService;
import com.parentalcontrol.device.service.TamperService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping(ApiConstants.DEVICES)
@RequiredArgsConstructor
@Tag(name = "Devices (Parent)", description = "Parent device management and pairing")
@SecurityRequirement(name = "bearerAuth")
public class ParentDeviceController {

  private final DeviceService deviceService;
  private final DeviceCommandService deviceCommandService;
  private final TamperService tamperService;

  @PostMapping("/pairing-code")
  @Operation(summary = "Generate a pairing code for child device")
  public ApiResponse<PairingCodeResponse> createPairingCode(
      @RequestBody(required = false) CreatePairingCodeRequest request) {
    return ApiResponse.ok(
        deviceService.createPairingCode(
            request != null ? request : new CreatePairingCodeRequest()));
  }

  @GetMapping("/pairing-code/{code}/status")
  @Operation(summary = "Check whether an active pairing code has been claimed")
  public ApiResponse<PairingCodeStatusResponse> getPairingCodeStatus(@PathVariable String code) {
    return ApiResponse.ok(deviceService.getPairingCodeStatus(code));
  }

  @GetMapping
  @Operation(summary = "List all devices for authenticated parent")
  public ApiResponse<List<DeviceResponse>> listDevices() {
    return ApiResponse.ok(deviceService.listDevicesForParent());
  }

  @GetMapping("/{deviceId}")
  @Operation(summary = "Get device details")
  public ApiResponse<DeviceResponse> getDevice(@PathVariable UUID deviceId) {
    return ApiResponse.ok(deviceService.getDevice(deviceId));
  }

  @PostMapping("/{deviceId}/commands")
  @Operation(summary = "Send remote command to child device (kiosk, revoke monitoring)")
  public ApiResponse<DeviceCommandResponse> sendCommand(
      @PathVariable UUID deviceId, @Valid @RequestBody DeviceCommandRequest request) {
    return ApiResponse.ok(deviceCommandService.sendCommand(deviceId, request));
  }

  @GetMapping("/{deviceId}/tamper-code")
  @Operation(summary = "Active verification code for device-admin removal attempt")
  public ApiResponse<TamperCodeResponse> getTamperCode(@PathVariable UUID deviceId) {
    return ApiResponse.ok(tamperService.getActiveCodeForParent(deviceId));
  }

  @PostMapping("/{deviceId}/wake")
  @Operation(
      summary = "Nudge a child device via FCM so it posts a fresh heartbeat",
      description =
          "Parent-initiated wake for a specific device. Used before starting live view when the"
              + " device looks briefly offline (Wi-Fi handoff, radio recovery). Best-effort — the"
              + " caller should still poll GET /devices/{deviceId} for a newer lastSeenAt.")
  public ApiResponse<DeviceWakeResponse> wakeDevice(@PathVariable UUID deviceId) {
    return ApiResponse.ok(deviceService.wakeDevice(deviceId));
  }
}
