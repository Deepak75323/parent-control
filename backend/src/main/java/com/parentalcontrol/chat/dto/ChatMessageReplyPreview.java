package com.parentalcontrol.chat.dto;

import com.parentalcontrol.chat.domain.ChatMessageType;
import java.util.UUID;
import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class ChatMessageReplyPreview {
  UUID messageId;
  String senderLabel;
  String senderNickname;
  ChatMessageType messageType;
  /** Short preview text, media label, or empty when deleted. */
  String bodyPreview;
  boolean deleted;
}
