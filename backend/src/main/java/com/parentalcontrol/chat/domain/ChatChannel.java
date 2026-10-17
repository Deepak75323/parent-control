package com.parentalcontrol.chat.domain;

import com.parentalcontrol.common.domain.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "chat_channels")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ChatChannel extends BaseEntity {

  @Column(name = "room_id", nullable = false)
  private UUID roomId;

  @Column(nullable = false, length = 64)
  private String name;

  @Enumerated(EnumType.STRING)
  @Column(name = "channel_type", nullable = false, length = 16)
  private ChatChannelType channelType;

  @Column(nullable = false)
  @Builder.Default
  private int position = 0;
}
