package com.parentalcontrol.monitoring.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.UUID;

@Data
public class SignalingMessageRequest {

    @NotNull
    private UUID sessionId;

    @NotBlank
    private String messageType;

    @NotBlank
    private String payload;
}
