package com.parentalcontrol.chat.dto;

import com.parentalcontrol.chat.domain.ChatChannelType;
import java.time.Instant;
import java.util.UUID;
import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class ChatChannelResponse {
  UUID id;
  UUID roomId;
  String name;
  ChatChannelType channelType;
  int position;
  Instant createdAt;
}
