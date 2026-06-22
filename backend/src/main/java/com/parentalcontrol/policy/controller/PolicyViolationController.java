package com.parentalcontrol.policy.controller;

import com.parentalcontrol.common.api.ApiResponse;
import com.parentalcontrol.policy.dto.PolicyViolationRequest;
import com.parentalcontrol.policy.dto.PolicyViolationResponse;
import com.parentalcontrol.policy.service.ViolationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/events")
@RequiredArgsConstructor
@Tag(name = "Events", description = "Child device events")
@SecurityRequirement(name = "bearerAuth")
public class PolicyViolationController {

    private final ViolationService violationService;

    @PostMapping("/policy-violations")
    @Operation(summary = "Report a policy violation from child device")
    public ApiResponse<PolicyViolationResponse> reportViolation(
            @Valid @RequestBody PolicyViolationRequest request) {
        return ApiResponse.ok(violationService.recordViolation(request));
    }
}
