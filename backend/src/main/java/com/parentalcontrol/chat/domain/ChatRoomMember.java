package com.parentalcontrol.chat.domain;

import com.parentalcontrol.common.domain.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "chat_room_members")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ChatRoomMember extends BaseEntity {

  @Column(name = "room_id", nullable = false)
  private UUID roomId;

  @Column(name = "user_id", nullable = false)
  private UUID userId;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 16)
  @Builder.Default
  private ChatRoomMemberRole role = ChatRoomMemberRole.MEMBER;

  @Column(name = "joined_at", nullable = false)
  @Builder.Default
  private Instant joinedAt = Instant.now();

  /** When set, messages at or before this time are hidden for this member only. */
  @Column(name = "chat_cleared_at")
  private Instant chatClearedAt;

  /** When set, incoming messages at or after this time show as unread for this member. */
  @Column(name = "unread_from_at")
  private Instant unreadFromAt;
}
