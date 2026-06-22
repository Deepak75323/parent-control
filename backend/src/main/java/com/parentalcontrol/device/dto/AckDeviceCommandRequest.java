package com.parentalcontrol.device.dto;

import lombok.Data;

@Data
public class AckDeviceCommandRequest {
    private boolean success = true;
    private String errorMessage;
}
