package com.parentalcontrol.monitoring.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import lombok.Data;

import java.util.List;

@Data
public class IngestNotificationsRequest {

    @NotEmpty
    @Valid
    private List<NotificationItemRequest> notifications;
}
