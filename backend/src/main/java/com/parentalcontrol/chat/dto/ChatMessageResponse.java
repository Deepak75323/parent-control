package com.parentalcontrol.chat.dto;

import java.time.Instant;
import java.util.UUID;
import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class ChatMessageResponse {
  UUID id;
  UUID roomId;
  UUID senderUserId;
  String senderLabel;
  String body;
  Instant createdAt;
}
