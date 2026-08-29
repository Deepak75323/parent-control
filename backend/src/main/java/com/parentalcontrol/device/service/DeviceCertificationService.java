package com.parentalcontrol.device.service;

import com.parentalcontrol.device.domain.Device;
import com.parentalcontrol.device.dto.HeartbeatRequest;
import com.parentalcontrol.observability.OemObservabilityService;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/** Processes OEM diagnostics from heartbeats and certification ingest. */
@Service
@RequiredArgsConstructor
public class DeviceCertificationService {

  private final OemObservabilityService oemObservabilityService;

  public void applyHeartbeatOemData(Device device, HeartbeatRequest request) {
    if (request.getOemVendor() != null) {
      device.setOemVendor(request.getOemVendor());
    }
    if (request.getAndroidSdkInt() != null) {
      device.setAndroidSdkInt(request.getAndroidSdkInt());
    }
    if (request.getDeviceReadinessScore() != null) {
      device.setDeviceReadinessScore(request.getDeviceReadinessScore());
    }
    if (request.getAndroid16ReadinessScore() != null) {
      device.setAndroid16ReadinessScore(request.getAndroid16ReadinessScore());
    }
    if (request.getOemSetupComplete() != null) {
      device.setOemSetupComplete(request.getOemSetupComplete());
    }

    Map<String, Object> diagnostics =
        request.getOemDiagnostics() != null
            ? new java.util.LinkedHashMap<>(request.getOemDiagnostics())
            : new java.util.LinkedHashMap<>();
    mergeMonitoringFields(diagnostics, request);
    mergeFlatReadinessFields(diagnostics, request);
    mergeFailureClassifications(device, diagnostics, request);
    if (!diagnostics.isEmpty() || request.getOemDiagnostics() != null) {
      device.setOemDiagnosticsJson(diagnostics);
      device.setOemFailureClassifications(extractFailureClassifications(diagnostics));
    }

    oemObservabilityService.recordHeartbeatOem(
        device.getOemVendor() != null ? device.getOemVendor() : "unknown",
        device.getDeviceReadinessScore(),
        device.getAndroid16ReadinessScore(),
        device.isOemSetupComplete(),
        device.getOemFailureClassifications());
  }

  private void mergeMonitoringFields(Map<String, Object> diagnostics, HeartbeatRequest request) {
    if (request.getMonitoringMode() != null) {
      diagnostics.put("monitoringMode", request.getMonitoringMode());
    }
    if (request.getMonitoringDegraded() != null) {
      diagnostics.put("monitoringDegraded", request.getMonitoringDegraded());
    }
    if (request.getMonitoringDegradedReason() != null) {
      diagnostics.put("monitoringDegradedReason", request.getMonitoringDegradedReason());
    }
    if (request.getStandbyBucket() != null) {
      diagnostics.put("standbyBucket", request.getStandbyBucket());
    }
    if (request.getDataSyncFgsUsedMs() != null) {
      diagnostics.put("dataSyncFgsUsedMs", request.getDataSyncFgsUsedMs());
    }
    if (request.getDataSyncFgsDailyCapMs() != null) {
      diagnostics.put("dataSyncFgsDailyCapMs", request.getDataSyncFgsDailyCapMs());
    }
    if (request.getDataSyncFgsExhausted() != null) {
      diagnostics.put("dataSyncFgsExhausted", request.getDataSyncFgsExhausted());
    }
  }

  /** Top-level heartbeat booleans so parent readiness survives partial oemDiagnostics payloads. */
  private void mergeFlatReadinessFields(Map<String, Object> diagnostics, HeartbeatRequest request) {
    putIfPresent(diagnostics, "batteryExempt", request.getBatteryExempt());
    putIfPresent(diagnostics, "autostartPromptDone", request.getAutostartPromptDone());
    putIfPresent(diagnostics, "overlayGranted", request.getOverlayGranted());
    putIfPresent(diagnostics, "cameraGranted", request.getCameraGranted());
    putIfPresent(
        diagnostics, "notificationPermissionGranted", request.getNotificationPermissionGranted());
    putIfPresent(
        diagnostics, "fullScreenIntentGranted", request.getFullScreenIntentGranted());
    if (request.getAccessibilityEnabled() != null) {
      diagnostics.put("accessibilityEnabled", request.getAccessibilityEnabled());
    }
    if (request.getAccessibilityServiceConnected() != null) {
      diagnostics.put("accessibilityServiceConnected", request.getAccessibilityServiceConnected());
    }
  }

  private static void putIfPresent(Map<String, Object> diagnostics, String key, Boolean value) {
    if (value != null) {
      diagnostics.put(key, value);
    }
  }

  private void mergeFailureClassifications(
      Device device, Map<String, Object> diagnostics, HeartbeatRequest request) {
    if (request.getFailureClassifications() == null) {
      return;
    }
    diagnostics.put("failureClassifications", request.getFailureClassifications());
    device.setOemFailureClassifications(request.getFailureClassifications());
  }

  public void applyCertificationResult(Device device, String status, int score, String vendor) {
    device.setCertificationStatus(status);
    device.setCertificationScore(score);
    device.setLastCertificationAt(java.time.Instant.now());
    if (vendor != null) {
      device.setOemVendor(vendor);
    }
    oemObservabilityService.recordCertificationRun(vendor, status, score);
  }

  @SuppressWarnings("unchecked")
  private List<String> extractFailureClassifications(Map<String, Object> diagnostics) {
    Object raw = diagnostics.get("failureClassifications");
    if (raw instanceof List<?> list) {
      List<String> out = new ArrayList<>();
      for (Object item : list) {
        if (item != null) {
          out.add(item.toString());
        }
      }
      return out;
    }
    return List.of();
  }
}
