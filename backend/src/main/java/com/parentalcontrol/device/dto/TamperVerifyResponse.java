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

  /** Refreshed when parent password verified online — child caches for offline unlock. */
  private OfflinePinCredentialDto offlinePin;
}
