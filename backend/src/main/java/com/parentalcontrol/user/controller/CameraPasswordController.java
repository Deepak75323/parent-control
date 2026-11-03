package com.parentalcontrol.user.controller;

import com.parentalcontrol.common.api.ApiResponse;
import com.parentalcontrol.common.constant.ApiConstants;
import com.parentalcontrol.user.dto.CameraPasswordRequest;
import com.parentalcontrol.user.dto.CameraPasswordStatusResponse;
import com.parentalcontrol.user.dto.CameraPasswordVerifyRequest;
import com.parentalcontrol.user.service.CameraPasswordService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping(ApiConstants.PARENT_CAMERA_PASSWORD)
@RequiredArgsConstructor
@Tag(
    name = "Camera password",
    description = "Password required to open camera live view — separate from the chat decrypt code")
@SecurityRequirement(name = "bearerAuth")
public class CameraPasswordController {

  private final CameraPasswordService cameraPasswordService;

  @GetMapping("/status")
  @Operation(summary = "Camera password configuration status")
  public ApiResponse<CameraPasswordStatusResponse> status() {
    return ApiResponse.ok(cameraPasswordService.status());
  }

  @PostMapping
  @Operation(summary = "Create camera password (first time)")
  public ApiResponse<Void> create(@Valid @RequestBody CameraPasswordRequest request) {
    cameraPasswordService.createPin(request.getPin(), request.getConfirmPin());
    return ApiResponse.okMessage("Camera password saved");
  }

  @PutMapping
  @Operation(summary = "Change camera password (requires current password)")
  public ApiResponse<Void> change(@Valid @RequestBody CameraPasswordRequest request) {
    cameraPasswordService.changePin(request.getCurrentPin(), request.getPin(), request.getConfirmPin());
    return ApiResponse.okMessage("Camera password updated");
  }

  @PostMapping("/verify")
  @Operation(summary = "Verify camera password when opening camera live view")
  public ApiResponse<CameraPasswordStatusResponse> verify(
      @Valid @RequestBody CameraPasswordVerifyRequest request) {
    boolean ok = cameraPasswordService.verifyPin(request.getPin());
    CameraPasswordStatusResponse status = cameraPasswordService.status();
    status.setVerified(ok);
    return ApiResponse.ok(status);
  }
}
