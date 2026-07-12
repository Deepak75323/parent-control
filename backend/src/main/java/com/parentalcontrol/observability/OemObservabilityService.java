package com.parentalcontrol.observability;

import io.micrometer.core.instrument.MeterRegistry;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/** OEM-specific fleet reliability metrics. */
@Service
@RequiredArgsConstructor
public class OemObservabilityService {

  private final MeterRegistry meterRegistry;

  public void recordHeartbeatOem(
      String vendor,
      int readinessScore,
      int android16ReadinessScore,
      boolean oemSetupComplete,
      List<String> failureClassifications) {
    meterRegistry
        .counter(
            "device.oem.heartbeat.total",
            "vendor",
            vendor != null ? vendor : "unknown",
            "oem_setup_complete",
            String.valueOf(oemSetupComplete))
        .increment();
    meterRegistry
        .summary("device.oem.readiness.score", "vendor", vendor != null ? vendor : "unknown")
        .record(readinessScore);
    meterRegistry
        .summary("device.android16.readiness.score", "vendor", vendor != null ? vendor : "unknown")
        .record(android16ReadinessScore);
    if (android16ReadinessScore < 70) {
      meterRegistry
          .counter(
              "device.android16.readiness.low.total", "vendor", vendor != null ? vendor : "unknown")
          .increment();
    }
    if (!oemSetupComplete) {
      meterRegistry.counter("device.oem.setup_incomplete.total", "vendor", vendor).increment();
    }
    if (failureClassifications != null) {
      for (String failure : failureClassifications) {
        meterRegistry
            .counter(
                "device.oem.failure.total",
                "vendor",
                vendor != null ? vendor : "unknown",
                "classification",
                failure)
            .increment();
      }
    }
  }

  public void recordCertificationRun(String vendor, String status, int score) {
    meterRegistry
        .counter(
            "device.oem.certification.total",
            "vendor",
            vendor != null ? vendor : "unknown",
            "status",
            status != null ? status : "unknown")
        .increment();
    meterRegistry
        .summary("device.oem.certification.score", "vendor", vendor != null ? vendor : "unknown")
        .record(score);
  }
}
