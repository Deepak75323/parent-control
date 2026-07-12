package com.parentalcontrol.device.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class DevicePairRequest {

  @NotBlank
  @Size(min = 6, max = 8)
  private String pairingCode;

  @NotBlank private String deviceName;

  private String deviceModel;
  private String osVersion;
  private String appVersion;

  @NotBlank private String androidId;

  private String fcmToken;

  /** Optional Play Integrity token (required when server enables attestation). */
  private String integrityToken;

  /** Client-reported local trust signals. */
  private DeviceTrustSignalDto trustSignals;

  /** Parent must accept monitoring disclaimer on child device */
  @NotBlank private String consentAccepted;
}
