package com.parentalcontrol.chat.dto;

import java.time.Instant;
import java.util.UUID;
import lombok.Builder;
import lombok.Value;

/** Partner presence for a 2-person chat room (may be hidden). */
@Value
@Builder
public class ChatPartnerPresenceResponse {
  UUID userId;
  boolean online;
  Instant lastSeenAt;
  boolean hidden;
}
