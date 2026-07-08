package com.parentalcontrol.device.dto;

import lombok.Builder;
import lombok.Data;

import java.time.Instant;

@Data
@Builder
public class TamperVerifyResponse {
    private boolean verified;
    /** ISO-8601 instant until which the child may complete device-admin deactivation. */
    private Instant unlockExpiresAt;
}
