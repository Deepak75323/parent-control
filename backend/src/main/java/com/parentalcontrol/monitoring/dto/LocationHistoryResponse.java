package com.parentalcontrol.monitoring.dto;

import lombok.Builder;
import lombok.Data;

import java.time.Instant;
import java.util.UUID;

@Data
@Builder
public class LocationHistoryResponse {

    private UUID id;
    private Double latitude;
    private Double longitude;
    private Double accuracyM;
    private Instant recordedAt;
}
