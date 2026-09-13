package com.parentalcontrol.friend.dto;

import java.time.Instant;
import java.util.UUID;
import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class FriendRequestResponse {
  UUID id;
  UUID requesterUserId;
  UUID addresseeUserId;
  String status;
  String identifier;
  String counterpartLabel;
  String counterpartNickname;
  UUID dmRoomId;
  Instant createdAt;
  Instant respondedAt;
}
