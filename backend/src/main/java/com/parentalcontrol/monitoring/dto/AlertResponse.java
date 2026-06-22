package com.parentalcontrol.monitoring.dto;

import com.parentalcontrol.monitoring.domain.AlertSeverity;
import lombok.Builder;
import lombok.Data;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

@Data
@Builder
public class AlertResponse {

    private UUID id;
    private UUID deviceId;
    private String alertType;
    private AlertSeverity severity;
    private String title;
    private String message;
    private Map<String, Object> payloadJson;
    private Instant readAt;
    private Instant createdAt;
}
