package com.parentalcontrol.monitoring.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.time.Instant;

@Data
public class UsageStatsItemRequest {

    @NotBlank
    private String packageName;

    @NotNull
    private Long usageMs;

    @NotNull
    private Instant recordedAt;
}
