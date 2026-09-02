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

  @Enumerated(EnumType.STRING)
  @Column(name = "message_type", nullable = false, length = 16)
  @Builder.Default
  private ChatMessageType messageType = ChatMessageType.TEXT;

  @Column(name = "attachment_storage_key", length = 512)
  private String attachmentStorageKey;

  @Column(name = "attachment_mime_type", length = 128)
  private String attachmentMimeType;

  @Column(name = "attachment_size_bytes")
  private Long attachmentSizeBytes;

  @Column(name = "attachment_file_name", length = 255)
  private String attachmentFileName;

  /** When true, attachment is deleted after the first non-sender views it. */
  @Column(name = "attachment_view_once", nullable = false)
  @Builder.Default
  private boolean attachmentViewOnce = false;

  @Column(name = "attachment_consumed_at")
  private Instant attachmentConsumedAt;

  @Column(name = "attachment_consumed_by_user_id")
  private UUID attachmentConsumedByUserId;

  @Column(name = "edited_at")
  private Instant editedAt;
}
