package com.parentalcontrol.device.dto;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class TamperAttemptResponse {
  private boolean parentPinConfigured;
  private String message;

  /** Present when parent password is configured — child caches for offline unlock. */
  private OfflinePinCredentialDto offlinePin;
}
