package com.parentalcontrol.monitoring.controller;

import com.parentalcontrol.common.api.ApiResponse;
import com.parentalcontrol.common.api.PageResponse;
import com.parentalcontrol.monitoring.dto.InstalledAppResponse;
import com.parentalcontrol.monitoring.dto.LocationHistoryResponse;
import com.parentalcontrol.monitoring.dto.NotificationResponse;
import com.parentalcontrol.monitoring.dto.UsageStatsResponse;
import com.parentalcontrol.monitoring.service.MonitoringService;
import com.parentalcontrol.policy.dto.GeofenceSummaryResponse;
import com.parentalcontrol.policy.service.PolicyService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/devices/{deviceId}")
@RequiredArgsConstructor
@Tag(name = "Monitoring (Parent)", description = "Parent monitoring data access")
@SecurityRequirement(name = "bearerAuth")
public class ParentMonitoringController {

    private final MonitoringService monitoringService;
    private final PolicyService policyService;

    @GetMapping("/notifications")
    @Operation(summary = "Paginated notification timeline for a device")
    public ApiResponse<PageResponse<NotificationResponse>> getNotifications(
            @PathVariable UUID deviceId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ApiResponse.ok(monitoringService.getNotifications(deviceId, page, size));
    }

    @GetMapping("/location-history")
    @Operation(summary = "Paginated location history for a device")
    public ApiResponse<PageResponse<LocationHistoryResponse>> getLocationHistory(
            @PathVariable UUID deviceId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size) {
        return ApiResponse.ok(monitoringService.getLocationHistory(deviceId, page, size));
    }

    @GetMapping("/usage-stats")
    @Operation(summary = "Paginated app usage history for a device")
    public ApiResponse<PageResponse<UsageStatsResponse>> getUsageStats(
            @PathVariable UUID deviceId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size) {
        return ApiResponse.ok(monitoringService.getUsageStats(deviceId, page, size));
    }

    @GetMapping("/installed-apps")
    @Operation(summary = "Installed apps on the child device (for blocking picker)")
    public ApiResponse<List<InstalledAppResponse>> getInstalledApps(@PathVariable UUID deviceId) {
        return ApiResponse.ok(monitoringService.getInstalledApps(deviceId));
    }

    @GetMapping("/geofences")
    @Operation(summary = "Active geofence zones for a device")
    public ApiResponse<List<GeofenceSummaryResponse>> getGeofences(@PathVariable UUID deviceId) {
        return ApiResponse.ok(policyService.getGeofencesForDevice(deviceId));
    }
}
