package com.parentalcontrol.chat.dto;

import com.parentalcontrol.chat.domain.ChatRoomMemberRole;
import java.time.Instant;
import java.util.UUID;
import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class ChatMemberResponse {
  UUID userId;
  String label;
  String nickname;
  ChatRoomMemberRole role;
  Instant joinedAt;
}
