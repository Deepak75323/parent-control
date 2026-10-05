package com.parentalcontrol.chat.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.UUID;
import lombok.Data;

@Data
public class SendChatMessageRequest {

  @NotBlank
  @Size(min = 1, max = 8000)
  private String body;

  /** Text channel; defaults to the server's first TEXT channel. */
  private UUID channelId;

  /** Optional message in the same room being quoted. */
  private UUID replyToMessageId;
}
