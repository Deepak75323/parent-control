package com.parentalcontrol.device.dto;

import lombok.Data;

@Data
public class RecordDeviceCommandExecutionRequest {
    private boolean success = true;
    private String errorMessage;
}
