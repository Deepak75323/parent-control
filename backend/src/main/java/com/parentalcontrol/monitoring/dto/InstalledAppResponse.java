package com.parentalcontrol.monitoring.dto;

import lombok.Builder;
import lombok.Data;

import java.time.Instant;
import java.util.UUID;

@Data
@Builder
public class InstalledAppResponse {

    private UUID id;
    private String packageName;
    private String appLabel;
    private String appCategory;
    private Instant syncedAt;
}
