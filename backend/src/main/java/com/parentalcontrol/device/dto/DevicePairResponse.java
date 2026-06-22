package com.parentalcontrol.device.dto;

import lombok.Builder;
import lombok.Data;

import java.util.UUID;

@Data
@Builder
public class DevicePairResponse {
    private UUID deviceId;
    private String deviceToken;
    /** Returned once at pair — store securely on device for HMAC signing. */
    private String deviceHmacKey;
    private String tokenType;
    private long expiresInSeconds;
    /** Parent account display name shown on child device after pairing. */
    private String parentDisplayName;
    private String deviceName;
}
