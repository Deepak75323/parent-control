package com.parentalcontrol.user.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class CameraPasswordRequest {

  /** Current password when changing an existing one. */
  @Size(max = 8)
  private String currentPin;

  @NotBlank
  @Size(min = 4, max = 8)
  private String pin;

  @NotBlank
  @Size(min = 4, max = 8)
  private String confirmPin;
}
