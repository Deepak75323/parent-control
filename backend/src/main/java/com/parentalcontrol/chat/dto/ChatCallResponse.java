package com.parentalcontrol.chat.dto;

import com.parentalcontrol.chat.domain.ChatRoomCallStatus;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class ChatCallResponse {
  UUID id;
  UUID roomId;
  UUID channelId;
  UUID startedByUserId;
  ChatRoomCallStatus status;
  Instant createdAt;
  List<ChatCallParticipantResponse> participants;
}
