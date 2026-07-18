package com.parentalcontrol.user.controller;

import com.parentalcontrol.common.api.ApiResponse;
import com.parentalcontrol.common.constant.ApiConstants;
import com.parentalcontrol.user.dto.FamilyHandleStatusResponse;
import com.parentalcontrol.user.dto.SetFamilyHandleRequest;
import com.parentalcontrol.user.service.FamilyHandleService;
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
@RequestMapping(ApiConstants.PARENT_FAMILY_HANDLE)
@RequiredArgsConstructor
@Tag(name = "Family handle", description = "Unique public handle for child first-time login")
@SecurityRequirement(name = "bearerAuth")
public class FamilyHandleController {

  private final FamilyHandleService familyHandleService;

  @GetMapping("/status")
  @Operation(summary = "Whether the family handle is configured")
  public ApiResponse<FamilyHandleStatusResponse> status() {
    return ApiResponse.ok(familyHandleService.getStatus());
  }

  @PostMapping
  @Operation(summary = "Set or change the family handle (must be unique)")
  public ApiResponse<FamilyHandleStatusResponse> setHandle(
      @Valid @RequestBody SetFamilyHandleRequest request) {
    return ApiResponse.ok(familyHandleService.setHandle(request.getFamilyHandle()));
  }
}
