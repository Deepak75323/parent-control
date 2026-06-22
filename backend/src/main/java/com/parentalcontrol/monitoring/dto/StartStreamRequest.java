package com.parentalcontrol.monitoring.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.UUID;

@Data
public class StartStreamRequest {

    @NotNull
    private UUID deviceId;

    @NotBlank
    private String streamType;

    /** Required for first silent (Device Owner) screen capture until PIN is unlocked */
    private String parentPin;

    /** Optional: user (front) or environment (back) for CAMERA streams */
    private String cameraFacing;
}
