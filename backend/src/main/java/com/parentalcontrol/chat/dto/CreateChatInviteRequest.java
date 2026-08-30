package com.parentalcontrol.chat.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class CreateChatInviteRequest {

  /** Family handle (@name) or parent account email. */
  @NotBlank
  @Size(min = 3, max = 320)
  private String identifier;
}
