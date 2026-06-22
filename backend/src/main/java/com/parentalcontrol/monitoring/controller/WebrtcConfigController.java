package com.parentalcontrol.monitoring.controller;

import com.parentalcontrol.common.api.ApiResponse;
import com.parentalcontrol.common.security.SecurityUtils;
import com.parentalcontrol.monitoring.dto.IceConnectivityReportRequest;
import com.parentalcontrol.monitoring.dto.WebrtcIceConfigResponse;
import com.parentalcontrol.monitoring.service.TurnObservabilityService;
import com.parentalcontrol.monitoring.service.WebrtcConfigService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/monitoring")
@RequiredArgsConstructor
@Tag(name = "WebRTC", description = "ICE server configuration and connectivity telemetry")
@SecurityRequirement(name = "bearerAuth")
public class WebrtcConfigController {

    private final WebrtcConfigService webrtcConfigService;
    private final TurnObservabilityService turnObservabilityService;

    @GetMapping("/webrtc-config")
    @Operation(summary = "STUN/TURN servers with ephemeral credentials for WebRTC clients")
    public ApiResponse<WebrtcIceConfigResponse> iceConfig(
            @RequestParam(value = "sessionId", required = false) UUID sessionId) {
        UUID subjectId = SecurityUtils.getDeviceId().orElseGet(SecurityUtils::requireUserId);
        return ApiResponse.ok(webrtcConfigService.buildIceConfig(subjectId, sessionId));
    }

    @PostMapping("/webrtc-ice-report")
    @Operation(summary = "Report ICE connection outcome for TURN/WebRTC observability")
    public ApiResponse<Void> reportIceConnectivity(@Valid @RequestBody IceConnectivityReportRequest request) {
        turnObservabilityService.recordIceReport(request);
        return ApiResponse.okMessage("ICE report recorded");
    }
}
