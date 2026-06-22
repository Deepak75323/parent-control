package com.parentalcontrol.device.dto;

import com.parentalcontrol.device.domain.DeviceCommandType;
import lombok.Builder;
import lombok.Data;

import java.util.UUID;

@Data
@Builder
public class PendingDeviceCommandResponse {
    private UUID id;
    private DeviceCommandType commandType;
    private String payload;
}
