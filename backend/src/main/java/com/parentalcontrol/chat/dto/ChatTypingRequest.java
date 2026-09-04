package com.parentalcontrol.chat.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class ChatTypingRequest {
  @NotNull private Boolean typing;
}
