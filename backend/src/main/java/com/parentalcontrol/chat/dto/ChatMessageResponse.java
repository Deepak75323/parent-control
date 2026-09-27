package com.parentalcontrol.chat.dto;

import com.parentalcontrol.chat.domain.ChatMessageType;
import com.parentalcontrol.chat.domain.ChatReceiptStatus;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class ChatMessageResponse {
  UUID id;
  UUID roomId;
  UUID channelId;
  UUID senderUserId;
  String senderLabel;
  String senderNickname;
  ChatMessageType messageType;
  String body;
  String attachmentUrl;
  String attachmentMimeType;
  String attachmentFileName;
  Long attachmentSizeBytes;
  boolean attachmentViewOnce;
  boolean attachmentConsumed;
  boolean ownMessage;
  boolean canViewAttachment;
  Instant createdAt;
  Instant editedAt;
  /** SENT, DELIVERED, or READ — only set on own messages. */
  ChatReceiptStatus receiptStatus;
  /** True when this incoming message has not been read by the viewer yet. */
  Boolean unread;
  /** True when the message was deleted for everyone and is visible only to the room owner. */
  boolean deleted;
  /** Quoted parent message, when this message is a reply. */
  ChatMessageReplyPreview replyTo;
  /** Emoji reactions grouped by emoji. */
  List<ChatMessageReactionSummary> reactions;
}
