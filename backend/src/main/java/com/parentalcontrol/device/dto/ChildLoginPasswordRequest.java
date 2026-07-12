package com.parentalcontrol.device.dto;

import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class ChildLoginPasswordRequest {

  @Size(min = 4, max = 128)
  private String childLoginPassword;
}
