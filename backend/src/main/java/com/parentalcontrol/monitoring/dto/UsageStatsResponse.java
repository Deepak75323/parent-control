package com.parentalcontrol.monitoring.dto;

import java.time.Instant;
import java.util.UUID;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class UsageStatsResponse {

  private UUID id;
  private UUID deviceId;
  private String packageName;
  private Long usageMs;
  private Instant recordedAt;
}
