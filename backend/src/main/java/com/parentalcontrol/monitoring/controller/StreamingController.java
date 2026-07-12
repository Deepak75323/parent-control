package com.parentalcontrol.monitoring.controller;

import com.parentalcontrol.common.api.ApiResponse;
import com.parentalcontrol.common.constant.ApiConstants;
import com.parentalcontrol.monitoring.dto.ScreenShareSessionResponse;
import com.parentalcontrol.monitoring.dto.StartScreenShareRequest;
import com.parentalcontrol.monitoring.dto.StartStreamRequest;
import com.parentalcontrol.monitoring.service.StreamingService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping(ApiConstants.MONITORING)
@RequiredArgsConstructor
@Tag(name = "Streaming", description = "Live monitoring session control")
@SecurityRequirement(name = "bearerAuth")
public class StreamingController {

  private final StreamingService streamingService;

  @PostMapping("/start-screen-share")
  @Operation(summary = "Start a screen share signaling session")
  public ApiResponse<ScreenShareSessionResponse> startScreenShare(
      @Valid @RequestBody StartScreenShareRequest request) {
    return ApiResponse.ok(streamingService.startScreenShare(request));
  }

  @PostMapping("/start-stream")
  @Operation(summary = "Start screen, camera, or audio stream")
  public ApiResponse<ScreenShareSessionResponse> startStream(
      @Valid @RequestBody StartStreamRequest request) {
    return ApiResponse.ok(streamingService.startStream(request));
  }

  @PostMapping("/stop-screen-share/{sessionId}")
  @Operation(summary = "End a screen share session")
  public ApiResponse<ScreenShareSessionResponse> stopScreenShare(@PathVariable UUID sessionId) {
    return ApiResponse.ok(streamingService.stopScreenShare(sessionId));
  }

  @PostMapping("/switch-camera/{sessionId}")
  @Operation(summary = "Switch front/back camera on an active camera stream")
  public ApiResponse<Void> switchCamera(@PathVariable UUID sessionId) {
    streamingService.switchCamera(sessionId);
    return ApiResponse.okMessage("Camera switch requested");
  }
}
