package com.parentalcontrol.friend.dto;

import java.util.UUID;
import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class FriendSummaryResponse {
  UUID friendshipId;
  UUID userId;
  String label;
  String nickname;
  UUID dmRoomId;
}
