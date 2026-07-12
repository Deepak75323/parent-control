package com.parentalcontrol.policy.controller;

import com.parentalcontrol.common.api.ApiResponse;
import com.parentalcontrol.common.constant.ApiConstants;
import com.parentalcontrol.policy.dto.CreateTimeExtensionRequest;
import com.parentalcontrol.policy.dto.RespondTimeExtensionRequest;
import com.parentalcontrol.policy.dto.TimeExtensionResponse;
import com.parentalcontrol.policy.service.TimeExtensionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@Tag(name = "Time extensions")
public class TimeExtensionController {

  private final TimeExtensionService timeExtensionService;

  @PostMapping(ApiConstants.DEVICE_TIME_EXTENSION_REQUESTS)
  @Operation(summary = "Child requests more time for a blocked app")
  public ApiResponse<TimeExtensionResponse> create(
      @Valid @RequestBody CreateTimeExtensionRequest request) {
    return ApiResponse.ok(timeExtensionService.createRequest(request));
  }

  @GetMapping(ApiConstants.TIME_EXTENSION_REQUESTS_PENDING)
  @Operation(summary = "Parent lists pending time extension requests")
  public ApiResponse<List<TimeExtensionResponse>> listPending() {
    return ApiResponse.ok(timeExtensionService.listPendingForParent());
  }

  @GetMapping(ApiConstants.DEVICES + "/{deviceId}/time-extension-requests")
  @Operation(summary = "Parent lists time extension history for a device")
  public ApiResponse<List<TimeExtensionResponse>> listForDevice(@PathVariable UUID deviceId) {
    return ApiResponse.ok(timeExtensionService.listForDevice(deviceId));
  }

  @PostMapping(ApiConstants.TIME_EXTENSION_REQUESTS + "/{requestId}/respond")
  @Operation(summary = "Parent approves or denies a time extension request")
  public ApiResponse<TimeExtensionResponse> respond(
      @PathVariable UUID requestId, @Valid @RequestBody RespondTimeExtensionRequest request) {
    return ApiResponse.ok(timeExtensionService.respond(requestId, request));
  }
}
