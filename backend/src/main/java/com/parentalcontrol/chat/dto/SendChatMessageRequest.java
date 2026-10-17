package com.parentalcontrol.chat.dto;

import com.parentalcontrol.common.constant.ValidationConstants;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.UUID;
import lombok.Data;

@Data
public class SendChatMessageRequest {

  @NotBlank
  @Size(min = 1, max = ValidationConstants.CHAT_MESSAGE_BODY_MAX_LENGTH)
  private String body;

  /** Text channel; defaults to the server's first TEXT channel. */
  private UUID channelId;

  /** Optional message in the same room being quoted. */
  private UUID replyToMessageId;
}
