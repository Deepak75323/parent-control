package com.parentalcontrol.policy.controller;

import com.parentalcontrol.common.api.ApiResponse;
import com.parentalcontrol.policy.dto.CreateTimeExtensionRequest;
import com.parentalcontrol.policy.dto.RespondTimeExtensionRequest;
import com.parentalcontrol.policy.dto.TimeExtensionResponse;
import com.parentalcontrol.policy.service.TimeExtensionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequiredArgsConstructor
@Tag(name = "Time extensions")
public class TimeExtensionController {

    private final TimeExtensionService timeExtensionService;

    @PostMapping("/api/v1/device/time-extension-requests")
    @Operation(summary = "Child requests more time for a blocked app")
    public ApiResponse<TimeExtensionResponse> create(@Valid @RequestBody CreateTimeExtensionRequest request) {
        return ApiResponse.ok(timeExtensionService.createRequest(request));
    }

    @GetMapping("/api/v1/time-extension-requests/pending")
    @Operation(summary = "Parent lists pending time extension requests")
    public ApiResponse<List<TimeExtensionResponse>> listPending() {
        return ApiResponse.ok(timeExtensionService.listPendingForParent());
    }

    @GetMapping("/api/v1/devices/{deviceId}/time-extension-requests")
    @Operation(summary = "Parent lists time extension history for a device")
    public ApiResponse<List<TimeExtensionResponse>> listForDevice(@PathVariable UUID deviceId) {
        return ApiResponse.ok(timeExtensionService.listForDevice(deviceId));
    }

    @PostMapping("/api/v1/time-extension-requests/{requestId}/respond")
    @Operation(summary = "Parent approves or denies a time extension request")
    public ApiResponse<TimeExtensionResponse> respond(
            @PathVariable UUID requestId,
            @Valid @RequestBody RespondTimeExtensionRequest request) {
        return ApiResponse.ok(timeExtensionService.respond(requestId, request));
    }
}
