package com.parentalcontrol.observability.dto;

import lombok.Data;

import java.util.Map;

@Data
public class MobileTelemetryEventDto {
    private String category;
    private String event;
    private Long timestampMs;
    private Map<String, Object> attributes;
}
