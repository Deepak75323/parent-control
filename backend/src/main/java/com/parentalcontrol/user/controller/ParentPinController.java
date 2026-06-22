package com.parentalcontrol.user.controller;

import com.parentalcontrol.common.api.ApiResponse;
import com.parentalcontrol.user.dto.ParentPinRequest;
import com.parentalcontrol.user.dto.ParentPinStatusResponse;
import com.parentalcontrol.user.service.ParentPinService;
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
@RequestMapping("/api/v1/parent/pin")
@RequiredArgsConstructor
@Tag(name = "Parent PIN", description = "PIN for sensitive actions including silent screen capture")
@SecurityRequirement(name = "bearerAuth")
public class ParentPinController {

    private final ParentPinService parentPinService;

    @PostMapping
    @Operation(summary = "Set or update parent PIN")
    public ApiResponse<Void> setPin(@Valid @RequestBody ParentPinRequest request) {
        parentPinService.setPin(request.getPin());
        return ApiResponse.okMessage("PIN saved");
    }

    @PostMapping("/verify")
    @Operation(summary = "Verify PIN and unlock silent screen capture")
    public ApiResponse<ParentPinStatusResponse> verify(@Valid @RequestBody ParentPinRequest request) {
        boolean ok = parentPinService.verifyPin(request.getPin());
        return ApiResponse.ok(ParentPinStatusResponse.builder()
                .verified(ok)
                .silentStreamUnlocked(ok && parentPinService.isSilentStreamUnlocked())
                .build());
    }

    @GetMapping("/status")
    @Operation(summary = "PIN configuration status")
    public ApiResponse<ParentPinStatusResponse> status() {
        return ApiResponse.ok(ParentPinStatusResponse.builder()
                .pinConfigured(parentPinService.hasPinConfigured())
                .silentStreamUnlocked(parentPinService.isSilentStreamUnlocked())
                .build());
    }
}
