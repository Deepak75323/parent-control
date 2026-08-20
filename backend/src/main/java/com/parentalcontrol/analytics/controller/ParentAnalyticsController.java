package com.parentalcontrol.analytics.controller;

import com.parentalcontrol.analytics.dto.ClientAnalyticsBatchRequest;
import com.parentalcontrol.analytics.service.ClientAnalyticsService;
import com.parentalcontrol.common.api.ApiResponse;
import com.parentalcontrol.common.constant.ApiConstants;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping(ApiConstants.PARENT_ANALYTICS)
@RequiredArgsConstructor
@Tag(name = "Parent analytics", description = "Client-side UI analytics from parent app")
@SecurityRequirement(name = "bearerAuth")
public class ParentAnalyticsController {

  private final ClientAnalyticsService clientAnalyticsService;

  @PostMapping("/batch")
  @Operation(summary = "Ingest batched parent UI analytics events")
  public ApiResponse<Void> ingestBatch(@Valid @RequestBody ClientAnalyticsBatchRequest request) {
    clientAnalyticsService.ingestParentBatch(request);
    return ApiResponse.ok(null);
  }
}
