package com.parentalcontrol.policy.controller;

import com.parentalcontrol.common.api.ApiResponse;
import com.parentalcontrol.monitoring.dto.ScreenShareSessionResponse;
import com.parentalcontrol.monitoring.service.StreamingService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/child/streaming")
@RequiredArgsConstructor
@Tag(name = "Child Streaming", description = "Child streaming sessions")
@SecurityRequirement(name = "bearerAuth")
public class ChildStreamingController {

    private final StreamingService streamingService;

    @GetMapping("/pending")
    @Operation(summary = "List pending stream sessions for child device")
    public ApiResponse<List<ScreenShareSessionResponse>> pending() {
        return ApiResponse.ok(streamingService.pendingSessionsForDevice());
    }

    @GetMapping("/active")
    @Operation(summary = "Current ACTIVE stream session for child device (if any)")
    public ApiResponse<ScreenShareSessionResponse> active() {
        return streamingService.activeSessionForDevice()
                .map(ApiResponse::ok)
                .orElseGet(() -> ApiResponse.ok(null));
    }
}
