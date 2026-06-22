package com.parentalcontrol.device.dto;

import com.parentalcontrol.device.domain.DeviceCommandStatus;
import com.parentalcontrol.device.domain.DeviceCommandType;
import lombok.Builder;
import lombok.Data;

import java.time.Instant;
import java.util.UUID;

@Data
@Builder
public class DeviceCommandResponse {
    private UUID id;
    private UUID deviceId;
    private DeviceCommandType commandType;
    private DeviceCommandStatus status;
    private Instant createdAt;
}
