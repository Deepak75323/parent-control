package com.parentalcontrol.monitoring.dto;

import java.time.Instant;
import java.util.UUID;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class UsageStatsRealtimeEvent {

  private UUID deviceId;
  private UUID id;
  private String packageName;
  private Long usageMs;
  private Instant recordedAt;
}
