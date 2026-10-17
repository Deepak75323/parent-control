package com.parentalcontrol.chat.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Value;

@Value
public class SetChatMessageReactionRequest {
  @NotBlank @Size(max = 32) String emoji;
}
