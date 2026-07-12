package com.parentalcontrol.device.dto;

import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class TamperVerifyRequest {
  /** Parent password / PIN configured in the parent app. */
  @Size(min = 4, max = 64)
  private String pin;

  /** One-time verification code from parent Alerts. */
  @Pattern(regexp = "^\\d{6}$", message = "Verification code must be 6 digits")
  private String code;

  /** {@code accessibility} or {@code device_admin} — used for audit logging. */
  @Size(max = 32)
  private String target;
}
