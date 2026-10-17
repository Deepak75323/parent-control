package com.parentalcontrol.chat.dto;

import java.time.Instant;
import java.util.UUID;
import lombok.Builder;
import lombok.Value;

/** Per-member presence for a room (hidden when share-presence is off). */
@Value
@Builder
public class ChatMemberPresenceResponse {
  UUID userId;
  boolean online;
  Instant lastSeenAt;
  /** True when the member opted out of sharing presence — treat as offline with no last seen. */
  boolean hidden;
}
