package com.parentalcontrol.portal.controller;

import com.parentalcontrol.common.api.ApiResponse;
import com.parentalcontrol.common.constant.ApiConstants;
import com.parentalcontrol.portal.dto.PortalControlResponse;
import com.parentalcontrol.portal.dto.PortalStatusResponse;
import com.parentalcontrol.portal.dto.SetPortalDisabledRequest;
import com.parentalcontrol.portal.dto.SetPortalShareRequest;
import com.parentalcontrol.portal.service.PortalSiteSettingsService;
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
@RequestMapping(ApiConstants.PORTAL)
@RequiredArgsConstructor
@Tag(name = "Portal", description = "Parent website availability (kill switch)")
public class PortalSiteController {

  private final PortalSiteSettingsService portalSiteSettingsService;

  @GetMapping("/status")
  @Operation(summary = "Public portal availability (no auth) — white screen when disabled")
  public ApiResponse<PortalStatusResponse> status() {
    return ApiResponse.ok(portalSiteSettingsService.publicStatus());
  }

  @GetMapping("/control")
  @SecurityRequirement(name = "bearerAuth")
  @Operation(summary = "Portal control flags for the signed-in parent")
  public ApiResponse<PortalControlResponse> control() {
    return ApiResponse.ok(portalSiteSettingsService.controlStatus());
  }

  @PostMapping("/disabled")
  @SecurityRequirement(name = "bearerAuth")
  @Operation(summary = "Disable or enable the parent website")
  public ApiResponse<PortalControlResponse> setDisabled(
      @Valid @RequestBody SetPortalDisabledRequest request) {
    return ApiResponse.ok(portalSiteSettingsService.setDisabled(Boolean.TRUE.equals(request.getDisabled())));
  }

  @PostMapping("/share")
  @SecurityRequirement(name = "bearerAuth")
  @Operation(summary = "Share disable/enable control with every parent (bootstrap admin only)")
  public ApiResponse<PortalControlResponse> setShare(
      @Valid @RequestBody SetPortalShareRequest request) {
    return ApiResponse.ok(
        portalSiteSettingsService.setSharedWithEveryone(
            Boolean.TRUE.equals(request.getShareWithEveryone())));
  }
}
