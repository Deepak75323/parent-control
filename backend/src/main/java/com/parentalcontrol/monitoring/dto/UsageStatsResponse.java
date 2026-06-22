package com.parentalcontrol.monitoring.dto;

import lombok.Builder;
import lombok.Data;

import java.time.Instant;
import java.util.UUID;

@Data
@Builder
public class UsageStatsResponse {

    private UUID id;
    private UUID deviceId;
    private String packageName;
    private Long usageMs;
    private Instant recordedAt;
}
