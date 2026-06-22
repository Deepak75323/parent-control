package com.parentalcontrol.device.dto;

import lombok.Data;

import java.util.UUID;

@Data
public class CreatePairingCodeRequest {
    /** Optional: link device to existing child profile */
    private UUID childProfileId;
}
