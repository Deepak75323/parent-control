package com.parentalcontrol.user.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class SetFamilyHandleRequest {

  @NotBlank
  @Size(min = 3, max = 32)
  private String familyHandle;
}
