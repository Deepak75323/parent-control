package com.parentalcontrol.monitoring.dto;

import lombok.Builder;
import lombok.Data;

import java.time.Instant;
import java.util.UUID;

@Data
@Builder
public class NotificationResponse {

    private UUID id;
    private String packageName;
    private String title;
    private String body;
    private Instant postedAt;
}
