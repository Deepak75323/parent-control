package com.parentalcontrol.monitoring.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.UUID;

@Data
public class StartScreenShareRequest {

    @NotNull
    private UUID deviceId;
}
