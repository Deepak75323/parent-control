package com.parentalcontrol.device.dto;

import com.parentalcontrol.device.domain.DeviceCommandType;
import java.util.UUID;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class PendingDeviceCommandResponse {
  private UUID id;
  private DeviceCommandType commandType;
  private String payload;
}
