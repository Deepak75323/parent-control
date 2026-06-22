package com.parentalcontrol.observability.dto;

import lombok.Data;

import java.util.List;

@Data
public class MobileTelemetryBatchRequest {
    private String correlationId;
    private String collectorVersion;
    private List<MobileTelemetryEventDto> events;
}
