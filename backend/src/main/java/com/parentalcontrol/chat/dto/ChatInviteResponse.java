package com.parentalcontrol.chat.dto;

import com.parentalcontrol.chat.domain.ChatRoomInviteStatus;
import java.time.Instant;
import java.util.UUID;
import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class ChatInviteResponse {
  UUID id;
  UUID roomId;
  String roomName;
  UUID inviterUserId;
  String inviterLabel;
  String inviterNickname;
  String inviteeIdentifier;
  ChatRoomInviteStatus status;
  Instant createdAt;
}
