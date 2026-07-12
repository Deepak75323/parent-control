package com.parentalcontrol.monitoring.kafka;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MonitoringEventMessage {
  private String eventType;
  private UUID deviceId;
  private Instant occurredAt;
  private Map<String, Object> payload;
}
