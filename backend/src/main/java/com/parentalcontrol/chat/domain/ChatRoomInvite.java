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
@Table(name = "chat_room_invites")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ChatRoomInvite extends BaseEntity {

  @Column(name = "room_id", nullable = false)
  private UUID roomId;

  @Column(name = "inviter_user_id", nullable = false)
  private UUID inviterUserId;

  @Column(name = "invitee_user_id", nullable = false)
  private UUID inviteeUserId;

  @Column(name = "invitee_identifier", nullable = false, length = 320)
  private String inviteeIdentifier;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 16)
  @Builder.Default
  private ChatRoomInviteStatus status = ChatRoomInviteStatus.PENDING;

  @Column(name = "responded_at")
  private Instant respondedAt;
}
