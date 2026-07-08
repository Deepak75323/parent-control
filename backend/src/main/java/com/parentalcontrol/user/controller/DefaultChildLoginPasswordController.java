package com.parentalcontrol.user.controller;

import com.parentalcontrol.common.api.ApiResponse;
import com.parentalcontrol.user.dto.DefaultChildLoginPasswordStatusResponse;
import com.parentalcontrol.user.dto.SetDefaultChildLoginPasswordRequest;
import com.parentalcontrol.user.service.DefaultChildLoginPasswordService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/parent/child-login-password")
@RequiredArgsConstructor
@Tag(name = "Child login password", description = "One family password for all child devices")
@SecurityRequirement(name = "bearerAuth")
public class DefaultChildLoginPasswordController {

    private final DefaultChildLoginPasswordService defaultChildLoginPasswordService;

    @GetMapping("/status")
    @Operation(summary = "Whether the family child login password is configured")
    public ApiResponse<DefaultChildLoginPasswordStatusResponse> status() {
        return ApiResponse.ok(defaultChildLoginPasswordService.getStatus());
    }

    @PostMapping
    @Operation(summary = "Set or change the family child login password for all linked devices")
    public ApiResponse<Void> setPassword(@Valid @RequestBody SetDefaultChildLoginPasswordRequest request) {
        defaultChildLoginPasswordService.setPassword(request.getChildLoginPassword());
        return ApiResponse.okMessage("Child login password updated for all devices");
    }
}
