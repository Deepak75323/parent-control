package com.parentalcontrol.device.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class OnboardingRecoveryRequest {

  @NotBlank
  @Size(max = 64)
  private String androidId;
}
