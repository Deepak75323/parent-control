package com.parentalcontrol.observability;

import io.micrometer.core.instrument.MeterRegistry;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class StompObservabilityService {

  private final MeterRegistry meterRegistry;

  public void recordPublish(String destination, String transport) {
    meterRegistry
        .counter(
            "stomp.messages.published.total",
            "destination",
            sanitize(destination),
            "transport",
            transport)
        .increment();
  }

  public void recordClusterFanout(String destination) {
    meterRegistry
        .counter("stomp.cluster.fanout.total", "destination", sanitize(destination))
        .increment();
  }

  private static String sanitize(String destination) {
    if (destination == null || destination.isBlank()) {
      return "unknown";
    }
    return destination
        .replaceAll("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}", "{id}")
        .replaceAll("/\\d+", "/{n}");
  }
}
