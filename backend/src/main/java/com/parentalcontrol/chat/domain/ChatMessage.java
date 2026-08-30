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
@Table(name = "chat_messages")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ChatMessage extends BaseEntity {

  @Column(name = "room_id", nullable = false)
  private UUID roomId;

  @Column(name = "sender_user_id", nullable = false)
  private UUID senderUserId;

  @Column(nullable = false, columnDefinition = "TEXT")
  private String body;
}
