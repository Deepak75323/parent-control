package com.parentalcontrol.device.dto;

import java.time.Instant;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class TamperCodeResponse {
  private boolean active;
  private String code;
  private Instant expiresAt;
}
