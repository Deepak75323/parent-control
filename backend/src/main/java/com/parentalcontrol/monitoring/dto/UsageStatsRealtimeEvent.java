package com.parentalcontrol.monitoring.dto;

import lombok.Builder;
import lombok.Data;

import java.time.Instant;
import java.util.UUID;

@Data
@Builder
public class UsageStatsRealtimeEvent {

    private UUID deviceId;
    private UUID id;
    private String packageName;
    private Long usageMs;
    private Instant recordedAt;
}
