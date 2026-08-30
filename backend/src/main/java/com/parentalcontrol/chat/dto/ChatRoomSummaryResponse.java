package com.parentalcontrol.chat.dto;

import java.time.Instant;
import java.util.UUID;
import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class ChatRoomSummaryResponse {
  UUID id;
  String name;
  UUID ownerUserId;
  boolean owner;
  int memberCount;
  Instant createdAt;
  Instant updatedAt;
}
