package com.parentalcontrol.policy.controller;

import com.parentalcontrol.common.api.ApiResponse;
import com.parentalcontrol.policy.domain.PolicyType;
import com.parentalcontrol.policy.dto.*;
import com.parentalcontrol.policy.service.PolicyService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/policies")
@RequiredArgsConstructor
@Tag(name = "Policies", description = "Parent policy management")
@SecurityRequirement(name = "bearerAuth")
public class ParentPolicyController {

    private final PolicyService policyService;

    @GetMapping
    @Operation(summary = "List policies for parent")
    public ApiResponse<List<PolicyResponse>> list(
            @RequestParam(required = false) PolicyType type) {
        return ApiResponse.ok(policyService.listPolicies(type));
    }

    @GetMapping("/devices/{deviceId}/bundle")
    @Operation(summary = "Get policy bundle for a device")
    public ApiResponse<PolicyBundleResponse> bundle(@PathVariable UUID deviceId) {
        return ApiResponse.ok(policyService.getPoliciesForDevice(deviceId));
    }

    @PostMapping("/app-block")
    public ApiResponse<PolicyResponse> createAppBlock(@Valid @RequestBody CreateAppBlockPolicyRequest request) {
        return ApiResponse.ok(policyService.createAppBlockPolicy(request));
    }

    @PostMapping("/screen-time")
    public ApiResponse<PolicyResponse> createScreenTime(@Valid @RequestBody CreateScreenTimePolicyRequest request) {
        return ApiResponse.ok(policyService.createScreenTimePolicy(request));
    }

    @PostMapping("/device-schedule")
    public ApiResponse<PolicyResponse> createDeviceSchedule(
            @Valid @RequestBody CreateDeviceSchedulePolicyRequest request) {
        return ApiResponse.ok(policyService.createDeviceSchedulePolicy(request));
    }

    @PostMapping("/geofence")
    public ApiResponse<PolicyResponse> createGeofence(@Valid @RequestBody CreateGeofencePolicyRequest request) {
        return ApiResponse.ok(policyService.createGeofencePolicy(request));
    }

    @PostMapping("/streaming")
    public ApiResponse<PolicyResponse> createStreaming(@Valid @RequestBody CreateStreamingPolicyRequest request) {
        return ApiResponse.ok(policyService.createStreamingPolicy(request));
    }

    @DeleteMapping("/{policyId}")
    public ApiResponse<Void> delete(@PathVariable UUID policyId) {
        policyService.deletePolicy(policyId);
        return ApiResponse.okMessage("Policy deleted");
    }
}
