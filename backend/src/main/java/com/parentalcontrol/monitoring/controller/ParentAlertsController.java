package com.parentalcontrol.monitoring.controller;

import com.parentalcontrol.common.api.ApiResponse;
import com.parentalcontrol.common.api.PageResponse;
import com.parentalcontrol.common.constant.ApiConstants;
import com.parentalcontrol.monitoring.dto.AlertResponse;
import com.parentalcontrol.monitoring.service.AlertService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping(ApiConstants.ALERTS)
@RequiredArgsConstructor
@Tag(name = "Alerts (Parent)", description = "Parent alert feed")
@SecurityRequirement(name = "bearerAuth")
public class ParentAlertsController {

  private final AlertService alertService;

  @GetMapping
  @Operation(summary = "List alerts for all devices owned by parent")
  public ApiResponse<PageResponse<AlertResponse>> listAlerts(
      @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "20") int size) {
    return ApiResponse.ok(alertService.listForParent(page, size));
  }
}
