package com.parentalcontrol.chat.dto;

import java.time.Instant;
import java.util.UUID;
import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class ChatCallParticipantResponse {
  UUID userId;
  String label;
  String nickname;
  Instant joinedAt;
}
