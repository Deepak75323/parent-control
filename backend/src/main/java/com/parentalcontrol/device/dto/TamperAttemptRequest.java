package com.parentalcontrol.device.dto;

import lombok.Data;

@Data
public class TamperAttemptRequest {
  /** {@code accessibility} or {@code device_admin} — used for audit logging. */
  private String target;
}
