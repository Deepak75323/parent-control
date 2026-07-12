package com.parentalcontrol.device.dto;

import com.parentalcontrol.device.domain.DeviceCommandStatus;
import com.parentalcontrol.device.domain.DeviceCommandType;
import java.time.Instant;
import java.util.UUID;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class DeviceCommandResponse {
  private UUID id;
  private UUID deviceId;
  private DeviceCommandType commandType;
  private DeviceCommandStatus status;
  private Instant createdAt;
}
