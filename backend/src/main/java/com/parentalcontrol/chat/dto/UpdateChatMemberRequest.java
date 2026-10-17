package com.parentalcontrol.chat.dto;

import com.parentalcontrol.chat.domain.ChatRoomMemberRole;
import lombok.Data;

/**
 * Partial update for a room member. Only non-null fields are applied. {@link #role} may only be
 * {@link ChatRoomMemberRole#ADMIN} or {@link ChatRoomMemberRole#MEMBER} (ownership transfer is a
 * separate endpoint).
 */
@Data
public class UpdateChatMemberRequest {
  private ChatRoomMemberRole role;
  private Boolean muted;
  private Boolean canDecrypt;
}
