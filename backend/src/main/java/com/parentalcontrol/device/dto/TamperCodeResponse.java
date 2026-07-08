package com.parentalcontrol.device.dto;

import lombok.Builder;
import lombok.Data;

import java.time.Instant;

@Data
@Builder
public class TamperCodeResponse {
    private boolean active;
    private String code;
    private Instant expiresAt;
}
