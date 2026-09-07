package com.parentalcontrol.chat.dto;

import com.parentalcontrol.chat.domain.ChatRoomMemberRole;
import java.time.Instant;
import java.util.List;
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
  /** Current viewer's membership role in this room. */
  ChatRoomMemberRole myRole;
  /** Current viewer cannot send when true. */
  boolean muted;
  /** Current viewer may unlock decrypt (owners always true). */
  boolean canDecrypt;
  int memberCount;
  Instant createdAt;
  Instant updatedAt;
  /** Per-member: local clear-chat timestamp (messages before this are hidden for this user). */
  Instant chatClearedAt;
  /** WhatsApp-style read cursor for this member in the room. */
  UUID lastReadMessageId;
  /** Incoming messages after {@link #lastReadMessageId} (excludes own). */
  int unreadCount;
  /** Text and voice channels in this server (room). */
  List<ChatChannelResponse> channels;
}
