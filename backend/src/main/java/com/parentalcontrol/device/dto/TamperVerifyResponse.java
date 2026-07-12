package com.parentalcontrol.device.dto;

import java.time.Instant;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class TamperVerifyResponse {
  private boolean verified;

  /** ISO-8601 instant until which the child may complete device-admin deactivation. */
  private Instant unlockExpiresAt;
}
