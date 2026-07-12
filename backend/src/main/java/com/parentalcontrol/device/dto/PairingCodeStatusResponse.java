package com.parentalcontrol.device.dto;

import java.util.UUID;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class PairingCodeStatusResponse {
  private String code;
  private boolean used;
  private boolean expired;
  private UUID deviceId;
  private String deviceName;
}
