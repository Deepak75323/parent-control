package com.parentalcontrol.user.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class ParentPinRequest {

  @NotBlank
  @Size(min = 4, max = 64)
  private String pin;
}
