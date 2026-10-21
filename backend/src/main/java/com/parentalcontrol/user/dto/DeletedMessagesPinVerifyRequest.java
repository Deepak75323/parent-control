package com.parentalcontrol.user.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class DeletedMessagesPinVerifyRequest {

  @NotBlank
  @Size(min = 4, max = 8)
  private String pin;
}
