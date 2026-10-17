package com.parentalcontrol.user.controller;

import com.parentalcontrol.chat.service.ParentPresenceFanoutService;
import com.parentalcontrol.common.api.ApiResponse;
import com.parentalcontrol.common.constant.ApiConstants;
import com.parentalcontrol.common.security.SecurityUtils;
import com.parentalcontrol.user.dto.SetSharePresenceRequest;
import com.parentalcontrol.user.dto.SharePresenceStatusResponse;
import com.parentalcontrol.user.service.SharePresenceSettingsService;
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
@RequestMapping(ApiConstants.PARENT_SHARE_PRESENCE)
@RequiredArgsConstructor
@Tag(name = "Share presence", description = "Online / Last seen privacy for parent chat")
@SecurityRequirement(name = "bearerAuth")
public class SharePresenceController {

  private final SharePresenceSettingsService sharePresenceSettingsService;
  private final ParentPresenceFanoutService parentPresenceFanoutService;

  @GetMapping("/status")
  @Operation(summary = "Whether this parent shares Online / Last seen with DM partners")
  public ApiResponse<SharePresenceStatusResponse> status() {
    return ApiResponse.ok(sharePresenceSettingsService.getStatus());
  }

  @PostMapping
  @Operation(summary = "Enable or disable sharing Online / Last seen with DM partners")
  public ApiResponse<SharePresenceStatusResponse> setEnabled(
      @Valid @RequestBody SetSharePresenceRequest request) {
    SharePresenceStatusResponse response =
        sharePresenceSettingsService.setEnabled(request.getEnabled());
    parentPresenceFanoutService.publishSharePreferenceChanged(
        SecurityUtils.requireUserId(), response.isEnabled());
    return ApiResponse.ok(response);
  }
}
