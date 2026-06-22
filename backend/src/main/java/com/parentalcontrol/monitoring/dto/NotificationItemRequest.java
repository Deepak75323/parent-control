package com.parentalcontrol.monitoring.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.time.Instant;

@Data
public class NotificationItemRequest {

    private String packageName;
    private String title;
    private String body;

    @NotNull
    private Instant postedAt;
}
