package com.parentalcontrol.chat.dto;

import java.util.UUID;
import lombok.Data;

@Data
public class StartChatCallRequest {
  /** Optional voice channel; defaults to the server's first VOICE channel. */
  private UUID channelId;
}
