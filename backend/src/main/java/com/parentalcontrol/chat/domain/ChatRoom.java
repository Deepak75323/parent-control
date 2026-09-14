package com.parentalcontrol.chat.domain;

import com.parentalcontrol.common.domain.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "chat_rooms")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ChatRoom extends BaseEntity {

  @Column(name = "owner_user_id", nullable = false)
  private UUID ownerUserId;

  @Column(nullable = false, length = 64)
  private String name;

  /** True for 1:1 friend DMs — not ordinary servers that happen to have 2 members. */
  @Column(name = "direct_message", nullable = false)
  @Builder.Default
  private boolean directMessage = false;

  /**
   * When true, non-owners see the Members roster panel. When false, only the owner sees it; others
   * get the older Parent chat help UI.
   */
  @Column(name = "members_panel_shared", nullable = false)
  @Builder.Default
  private boolean membersPanelShared = false;
}
