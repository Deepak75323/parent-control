package com.parentalcontrol.friend.domain;

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
@Table(name = "parent_friendships")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ParentFriendship extends BaseEntity {

  @Column(name = "requester_user_id", nullable = false)
  private UUID requesterUserId;

  @Column(name = "addressee_user_id", nullable = false)
  private UUID addresseeUserId;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 16)
  @Builder.Default
  private ParentFriendshipStatus status = ParentFriendshipStatus.PENDING;

  @Column(name = "invitee_identifier", nullable = false, length = 320)
  private String inviteeIdentifier;

  @Column(name = "dm_room_id")
  private UUID dmRoomId;

  @Column(name = "responded_at")
  private Instant respondedAt;
}
