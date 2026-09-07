package com.parentalcontrol.chat.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.UUID;
import lombok.Data;

@Data
public class ChatCallSignalRequest {
  @NotNull private UUID toUserId;
  @NotBlank private String messageType;
  @NotBlank private String payload;
}
