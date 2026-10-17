package com.parentalcontrol.chat.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.parentalcontrol.chat.domain.ChatMessageType;
import com.parentalcontrol.common.exception.BusinessException;
import com.parentalcontrol.config.ChatAttachmentProperties;
import java.nio.file.Path;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.mock.web.MockMultipartFile;

class ChatAttachmentStorageServiceTest {

  @TempDir Path tempDir;

  private ChatAttachmentStorageService storageService;

  @BeforeEach
  void setUp() {
    ChatAttachmentProperties properties = new ChatAttachmentProperties();
    properties.setStoragePath(tempDir.toString());
    storageService = new ChatAttachmentStorageService(properties);
  }

  @Test
  void validate_rejectsEmptyFile() {
    MockMultipartFile file = new MockMultipartFile("file", "photo.png", "image/png", new byte[0]);

    assertThatThrownBy(() -> storageService.validate(file))
        .isInstanceOf(BusinessException.class);
  }

  @Test
  void store_persistsPngAndReturnsMetadata() {
    byte[] bytes = new byte[] {(byte) 0x89, 0x50, 0x4E, 0x47};
    MockMultipartFile file = new MockMultipartFile("file", "photo.png", "image/png", bytes);
    UUID roomId = UUID.randomUUID();
    UUID messageId = UUID.randomUUID();

    ChatAttachmentStorageService.StoredAttachment stored =
        storageService.store(roomId, messageId, file);

    assertThat(stored.messageType()).isEqualTo(ChatMessageType.IMAGE);
    assertThat(stored.mimeType()).isEqualTo("image/png");
    assertThat(stored.originalFileName()).isEqualTo("photo.png");
    assertThat(stored.storageKey()).isEqualTo(roomId + "/" + messageId + ".png");
    assertThat(storageService.loadAsResource(stored.storageKey()).exists()).isTrue();
  }

  @Test
  void store_rejectsUnsupportedMimeType() {
    MockMultipartFile file =
        new MockMultipartFile("file", "notes.txt", "text/plain", "hello".getBytes());

    assertThatThrownBy(
            () -> storageService.store(UUID.randomUUID(), UUID.randomUUID(), file))
        .isInstanceOf(BusinessException.class);
  }

  @Test
  void readAndDelete_returnsBytesAndRemovesFile() {
    byte[] bytes = new byte[] {(byte) 0x89, 0x50, 0x4E, 0x47};
    MockMultipartFile file = new MockMultipartFile("file", "photo.png", "image/png", bytes);
    UUID roomId = UUID.randomUUID();
    UUID messageId = UUID.randomUUID();
    ChatAttachmentStorageService.StoredAttachment stored =
        storageService.store(roomId, messageId, file);

    byte[] read = storageService.readAndDelete(stored.storageKey());

    assertThat(read).isEqualTo(bytes);
    assertThatThrownBy(() -> storageService.loadAsResource(stored.storageKey()))
        .isInstanceOf(BusinessException.class);
    assertThatThrownBy(() -> storageService.readAndDelete(stored.storageKey()))
        .isInstanceOf(BusinessException.class);
  }
}
