package com.parentalcontrol.device.controller;

import com.parentalcontrol.common.api.ApiResponse;
import com.parentalcontrol.device.dto.OnboardingAuthRequest;
import com.parentalcontrol.device.dto.OnboardingAuthResponse;
import com.parentalcontrol.device.dto.OnboardingRecoveryRequest;
import com.parentalcontrol.device.dto.OnboardingRecoveryResponse;
import com.parentalcontrol.device.service.OnboardingAuthService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/onboarding")
@RequiredArgsConstructor
@Tag(name = "Child onboarding", description = "Pre-pairing child app authentication")
public class OnboardingController {

  private final OnboardingAuthService onboardingAuthService;

  @PostMapping("/authenticate")
  @Operation(summary = "Authenticate child setup before permission wizard")
  public ApiResponse<OnboardingAuthResponse> authenticate(
      @Valid @RequestBody OnboardingAuthRequest request) {
    return ApiResponse.ok(onboardingAuthService.authenticate(request));
  }

  @PostMapping("/recovery/request")
  @Operation(summary = "Send a recovery code to the parent app Alerts (paired devices)")
  public ApiResponse<OnboardingRecoveryResponse> requestRecovery(
      @Valid @RequestBody OnboardingRecoveryRequest request) {
    return ApiResponse.ok(onboardingAuthService.requestRecoveryCode(request));
  }
}
