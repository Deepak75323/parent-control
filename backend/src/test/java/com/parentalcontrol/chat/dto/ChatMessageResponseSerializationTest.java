package com.parentalcontrol.chat.dto;

import static org.assertj.core.api.Assertions.assertThatCode;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.parentalcontrol.chat.domain.ChatMessageType;
import com.parentalcontrol.chat.domain.ChatReceiptStatus;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class ChatMessageResponseSerializationTest {

  private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();

  @Test
  void serializesImageMessageForStompRedis() {
    ChatMessageResponse response =
        ChatMessageResponse.builder()
            .id(UUID.randomUUID())
            .roomId(UUID.randomUUID())
            .senderUserId(UUID.randomUUID())
            .senderLabel("@family")
            .senderNickname("Golden Aurora")
            .messageType(ChatMessageType.IMAGE)
            .body("")
            .attachmentMimeType("image/png")
            .attachmentFileName("photo.png")
            .attachmentSizeBytes(1024L)
            .attachmentViewOnce(true)
            .attachmentConsumed(false)
            .ownMessage(true)
            .canViewAttachment(false)
            .createdAt(Instant.parse("2026-09-02T03:59:20Z"))
            .editedAt(null)
            .receiptStatus(ChatReceiptStatus.SENT)
            .build();

    assertThatCode(() -> objectMapper.writeValueAsString(response)).doesNotThrowAnyException();
  }
}
