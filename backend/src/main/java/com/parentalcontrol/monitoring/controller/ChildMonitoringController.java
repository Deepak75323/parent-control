package com.parentalcontrol.monitoring.controller;

import com.parentalcontrol.common.api.ApiResponse;
import com.parentalcontrol.common.constant.ApiConstants;
import com.parentalcontrol.monitoring.dto.IngestInstalledAppsRequest;
import com.parentalcontrol.monitoring.dto.IngestNotificationsRequest;
import com.parentalcontrol.monitoring.dto.IngestResultResponse;
import com.parentalcontrol.monitoring.dto.IngestUsageStatsRequest;
import com.parentalcontrol.monitoring.dto.LocationHistoryResponse;
import com.parentalcontrol.monitoring.dto.LocationIngestRequest;
import com.parentalcontrol.monitoring.service.MonitoringService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping(ApiConstants.DEVICE)
@RequiredArgsConstructor
@Tag(name = "Monitoring (Child)", description = "Child device monitoring ingest")
public class ChildMonitoringController {

  private final MonitoringService monitoringService;

  @PostMapping("/notifications")
  @Operation(summary = "Batch ingest captured notifications")
  public ApiResponse<IngestResultResponse> ingestNotifications(
      @Valid @RequestBody IngestNotificationsRequest request) {
    return ApiResponse.ok(monitoringService.ingestNotifications(request));
  }

  @PostMapping("/location")
  @Operation(summary = "Record a location point")
  public ApiResponse<LocationHistoryResponse> ingestLocation(
      @Valid @RequestBody LocationIngestRequest request) {
    return ApiResponse.ok(monitoringService.ingestLocation(request));
  }

  @PostMapping("/usage-stats")
  @Operation(summary = "Batch ingest app usage statistics")
  public ApiResponse<IngestResultResponse> ingestUsageStats(
      @Valid @RequestBody IngestUsageStatsRequest request) {
    return ApiResponse.ok(monitoringService.ingestUsageStats(request));
  }

  @PostMapping("/installed-apps")
  @Operation(summary = "Replace installed app inventory snapshot")
  public ApiResponse<IngestResultResponse> ingestInstalledApps(
      @Valid @RequestBody IngestInstalledAppsRequest request) {
    return ApiResponse.ok(monitoringService.ingestInstalledApps(request));
  }
}
