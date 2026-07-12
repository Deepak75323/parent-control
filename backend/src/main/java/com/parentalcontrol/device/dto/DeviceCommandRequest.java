package com.parentalcontrol.device.dto;

import com.parentalcontrol.device.domain.DeviceCommandType;
import jakarta.validation.constraints.NotNull;
import java.util.Map;
import lombok.Data;

@Data
public class DeviceCommandRequest {

  @NotNull private DeviceCommandType commandType;

  private Map<String, Object> payload;

  /**
   * Optional parent PIN for step-up authentication on sensitive commands (remote control:
   * TAP/SWIPE/TYPE_TEXT/LAUNCH_APP, plus kiosk + revoke). Required when {@code
   * app.security.remote-control-pin-required=true} and the parent has not already verified the PIN
   * this session.
   */
  private String parentPin;
}
