package com.parentalcontrol.device.dto;

import lombok.Builder;
import lombok.Data;

import java.util.UUID;

@Data
@Builder
public class PairingCodeStatusResponse {
    private String code;
    private boolean used;
    private boolean expired;
    private UUID deviceId;
    private String deviceName;
}
