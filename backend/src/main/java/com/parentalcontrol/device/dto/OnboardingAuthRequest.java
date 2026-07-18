package com.parentalcontrol.device.dto;

import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class OnboardingAuthRequest {

  /** Setup / recovery code — required when setupCodeOnly is true. */
  @Size(min = 8, max = 8)
  private String pairingCode;

  /** Child device login password — required for normal login. */
  @Size(min = 4, max = 128)
  private String childLoginPassword;

  /**
   * Parent family handle — required for first-time (unpaired) password login so the child binds to
   * exactly one parent account.
   */
  @Size(min = 3, max = 32)
  private String familyHandle;

  /** When true, a valid unused pairing code alone proves parent authorization (recovery flow). */
  private boolean setupCodeOnly;

  /** 6-digit recovery code from parent Alerts (paired devices). */
  @Size(min = 6, max = 8)
  private String recoveryCode;

  /** Physical device id — used to verify password against a paired device record. */
  @Size(max = 64)
  private String androidId;
}
