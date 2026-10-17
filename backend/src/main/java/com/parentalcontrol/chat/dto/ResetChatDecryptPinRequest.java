package com.parentalcontrol.chat.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.Data;

@Data
public class ResetChatDecryptPinRequest {

  @NotBlank
  @Pattern(regexp = "^\\d{6}$", message = "Decrypt code must be exactly 6 digits")
  private String newPin;
}
