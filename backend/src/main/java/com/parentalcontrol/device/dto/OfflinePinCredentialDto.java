package com.parentalcontrol.device.dto;

import lombok.Builder;
import lombok.Data;

/** PBKDF2 material for child offline parent-password verification. */
@Data
@Builder
public class OfflinePinCredentialDto {
  private String saltBase64;
  private String hashBase64;
  private int iterations;
}
