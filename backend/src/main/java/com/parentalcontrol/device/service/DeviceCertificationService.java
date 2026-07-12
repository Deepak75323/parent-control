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
    if (request.getOemDiagnostics() != null) {
      device.setOemDiagnosticsJson(request.getOemDiagnostics());
      device.setOemFailureClassifications(
          extractFailureClassifications(request.getOemDiagnostics()));
    }

    oemObservabilityService.recordHeartbeatOem(
        device.getOemVendor() != null ? device.getOemVendor() : "unknown",
        device.getDeviceReadinessScore(),
        device.getAndroid16ReadinessScore(),
        device.isOemSetupComplete(),
        device.getOemFailureClassifications());
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
