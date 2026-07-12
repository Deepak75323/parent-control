package com.parentalcontrol.device.dto;

import java.util.UUID;
import lombok.Data;

@Data
public class CreatePairingCodeRequest {
  /** Optional: link device to existing child profile */
  private UUID childProfileId;
}
