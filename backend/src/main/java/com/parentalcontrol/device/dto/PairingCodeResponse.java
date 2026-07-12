package com.parentalcontrol.device.dto;

import java.time.Instant;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class PairingCodeResponse {
  private String code;
  private String qrPayload;
  private Instant expiresAt;
  private int expiresInSeconds;
}
