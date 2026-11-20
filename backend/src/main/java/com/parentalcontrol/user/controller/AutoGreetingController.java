package com.parentalcontrol.user.controller;

import com.parentalcontrol.common.api.ApiResponse;
import com.parentalcontrol.common.constant.ApiConstants;
import com.parentalcontrol.user.dto.AutoGreetingSettingsResponse;
import com.parentalcontrol.user.dto.SetAutoGreetingSettingsRequest;
import com.parentalcontrol.user.service.AutoGreetingSettingsService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping(ApiConstants.PARENT_AUTO_GREETING)
@RequiredArgsConstructor
@Tag(name = "Auto greeting", description = "Scheduled good morning / good night chat messages")
@SecurityRequirement(name = "bearerAuth")
public class AutoGreetingController {

  private final AutoGreetingSettingsService autoGreetingSettingsService;

  @GetMapping
  @Operation(summary = "Get auto greeting settings")
  public ApiResponse<AutoGreetingSettingsResponse> get() {
    return ApiResponse.ok(autoGreetingSettingsService.getSettings());
  }

  @PutMapping
  @Operation(summary = "Update auto greeting settings (enable, room, times, timezone)")
  public ApiResponse<AutoGreetingSettingsResponse> update(
      @Valid @RequestBody SetAutoGreetingSettingsRequest request) {
    return ApiResponse.ok(autoGreetingSettingsService.updateSettings(request));
  }
}
