package com.parentalcontrol.chat.dto;

import jakarta.validation.constraints.NotNull;
import java.util.UUID;
import lombok.Data;

@Data
public class MarkChatReadRequest {
  @NotNull private UUID lastMessageId;
}
