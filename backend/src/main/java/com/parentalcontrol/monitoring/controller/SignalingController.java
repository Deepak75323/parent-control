package com.parentalcontrol.monitoring.controller;

import com.parentalcontrol.common.api.ApiResponse;
import com.parentalcontrol.common.constant.ApiConstants;
import com.parentalcontrol.monitoring.dto.SignalingMessageRequest;
import com.parentalcontrol.monitoring.dto.SignalingMessageResponse;
import com.parentalcontrol.monitoring.service.SignalingService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping(ApiConstants.MONITORING_SIGNALING)
@RequiredArgsConstructor
@Tag(name = "Signaling", description = "WebRTC signaling")
@SecurityRequirement(name = "bearerAuth")
public class SignalingController {

  private final SignalingService signalingService;

  @PostMapping("/{roomId}")
  @Operation(summary = "Post offer, answer, or ICE candidate")
  public ApiResponse<SignalingMessageResponse> post(
      @PathVariable String roomId, @Valid @RequestBody SignalingMessageRequest request) {
    return ApiResponse.ok(signalingService.postMessage(roomId, request));
  }

  @GetMapping("/{roomId}")
  @Operation(summary = "Get recent signaling messages for a room")
  public ApiResponse<List<SignalingMessageResponse>> recent(@PathVariable String roomId) {
    return ApiResponse.ok(signalingService.getRecentMessages(roomId));
  }
}
