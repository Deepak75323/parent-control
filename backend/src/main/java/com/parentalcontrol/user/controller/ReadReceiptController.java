package com.parentalcontrol.user.controller;

import com.parentalcontrol.common.api.ApiResponse;
import com.parentalcontrol.common.constant.ApiConstants;
import com.parentalcontrol.user.dto.ReadReceiptStatusResponse;
import com.parentalcontrol.user.dto.SetReadReceiptsRequest;
import com.parentalcontrol.user.service.ReadReceiptSettingsService;
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
@RequestMapping(ApiConstants.PARENT_READ_RECEIPTS)
@RequiredArgsConstructor
@Tag(name = "Read receipts", description = "Chat read receipt privacy setting")
@SecurityRequirement(name = "bearerAuth")
public class ReadReceiptController {

  private final ReadReceiptSettingsService readReceiptSettingsService;

  @GetMapping("/status")
  @Operation(summary = "Whether read receipts are enabled for this parent")
  public ApiResponse<ReadReceiptStatusResponse> status() {
    return ApiResponse.ok(readReceiptSettingsService.getStatus());
  }

  @PostMapping
  @Operation(summary = "Enable or disable sending read receipts in chat")
  public ApiResponse<ReadReceiptStatusResponse> setEnabled(
      @Valid @RequestBody SetReadReceiptsRequest request) {
    return ApiResponse.ok(readReceiptSettingsService.setEnabled(request.getEnabled()));
  }
}
