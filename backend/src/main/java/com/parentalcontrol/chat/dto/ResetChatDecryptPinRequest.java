package com.parentalcontrol.chat.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.Data;

@Data
public class ResetChatDecryptPinRequest {

  @NotBlank
  @Pattern(regexp = "^\\d{4}$", message = "Decrypt PIN must be exactly 4 digits")
  private String newPin;
}
