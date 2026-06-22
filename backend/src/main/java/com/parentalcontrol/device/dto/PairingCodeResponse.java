package com.parentalcontrol.device.dto;

import lombok.Builder;
import lombok.Data;

import java.time.Instant;

@Data
@Builder
public class PairingCodeResponse {
    private String code;
    private String qrPayload;
    private Instant expiresAt;
    private int expiresInSeconds;
}
