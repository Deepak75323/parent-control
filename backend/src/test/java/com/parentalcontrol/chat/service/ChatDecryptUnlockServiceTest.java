package com.parentalcontrol.chat.service;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.parentalcontrol.chat.domain.ChatDecryptSettings;
import com.parentalcontrol.chat.domain.ChatRoom;
import com.parentalcontrol.chat.repository.ChatDecryptSettingsRepository;
import com.parentalcontrol.chat.repository.ChatRoomRepository;
import com.parentalcontrol.common.constant.MessageConstants;
import com.parentalcontrol.common.exception.BusinessException;
import com.parentalcontrol.common.security.SecurityUtils;
import com.parentalcontrol.config.ChatDecryptProperties;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

@ExtendWith(MockitoExtension.class)
class ChatDecryptUnlockServiceTest {

  @Mock private ChatRoomRepository roomRepository;
  @Mock private ChatDecryptSettingsRepository settingsRepository;
  @Mock private PasswordEncoder passwordEncoder;
  @Mock private ChatService chatService;

  private ChatDecryptProperties decryptProperties;
  private ChatDecryptUnlockService service;
  private MockedStatic<SecurityUtils> security;

  private final UUID ownerId = UUID.fromString("11111111-1111-1111-1111-111111111111");
  private final UUID otherId = UUID.fromString("22222222-2222-2222-2222-222222222222");
  private final UUID roomId = UUID.fromString("33333333-3333-3333-3333-333333333333");

  @BeforeEach
  void setUp() {
    decryptProperties = new ChatDecryptProperties();
    decryptProperties.setPin("4829");
    service =
        new ChatDecryptUnlockService(
            roomRepository, settingsRepository, decryptProperties, passwordEncoder, chatService);
    security = Mockito.mockStatic(SecurityUtils.class);
  }

  @AfterEach
  void tearDown() {
    security.close();
  }

  @Test
  void unlock_succeedsForOwnerWithEnvPin() {
    security.when(SecurityUtils::requireUserId).thenReturn(ownerId);
    when(roomRepository.findById(roomId)).thenReturn(Optional.of(ownedRoom()));
    when(settingsRepository.findById((short) 1)).thenReturn(Optional.empty());
    when(passwordEncoder.encode("4829")).thenReturn("hashed-4829");

    assertDoesNotThrow(() -> service.unlock(roomId, "4829"));
    verify(settingsRepository).save(any(ChatDecryptSettings.class));
  }

  @Test
  void unlock_succeedsForOwnerWithDbHash() {
    security.when(SecurityUtils::requireUserId).thenReturn(ownerId);
    when(roomRepository.findById(roomId)).thenReturn(Optional.of(ownedRoom()));
    when(settingsRepository.findById((short) 1))
        .thenReturn(
            Optional.of(
                ChatDecryptSettings.builder().id((short) 1).pinHash("hashed-4829").build()));
    when(passwordEncoder.matches("4829", "hashed-4829")).thenReturn(true);

    assertDoesNotThrow(() -> service.unlock(roomId, "4829"));
    verify(settingsRepository, never()).save(any());
  }

  @Test
  void unlock_rejectsNonOwner() {
    security.when(SecurityUtils::requireUserId).thenReturn(otherId);
    when(roomRepository.findById(roomId)).thenReturn(Optional.of(ownedRoom()));

    BusinessException ex =
        assertThrows(BusinessException.class, () -> service.unlock(roomId, "4829"));
    assertEquals(MessageConstants.CHAT_ONLY_OWNER_CAN_DECRYPT, ex.getMessage());
  }

  @Test
  void unlock_rejectsWrongPin() {
    security.when(SecurityUtils::requireUserId).thenReturn(ownerId);
    when(roomRepository.findById(roomId)).thenReturn(Optional.of(ownedRoom()));
    when(settingsRepository.findById((short) 1)).thenReturn(Optional.empty());

    BusinessException ex =
        assertThrows(BusinessException.class, () -> service.unlock(roomId, "0000"));
    assertEquals(MessageConstants.CHAT_DECRYPT_PIN_INVALID, ex.getMessage());
  }

  @Test
  void unlock_rejectsWhenPinNotConfigured() {
    decryptProperties.setPin("");
    security.when(SecurityUtils::requireUserId).thenReturn(ownerId);
    when(roomRepository.findById(roomId)).thenReturn(Optional.of(ownedRoom()));
    when(settingsRepository.findById((short) 1)).thenReturn(Optional.empty());

    BusinessException ex =
        assertThrows(BusinessException.class, () -> service.unlock(roomId, "4829"));
    assertEquals(MessageConstants.CHAT_DECRYPT_NOT_CONFIGURED, ex.getMessage());
  }

  @Test
  void createPin_persistsHashWhenUnset() {
    decryptProperties.setPin("");
    security.when(SecurityUtils::requireUserId).thenReturn(ownerId);
    when(settingsRepository.findById((short) 1)).thenReturn(Optional.empty());
    when(passwordEncoder.encode("1234")).thenReturn("hashed-1234");

    service.createPin("1234");

    ArgumentCaptor<ChatDecryptSettings> captor = ArgumentCaptor.forClass(ChatDecryptSettings.class);
    verify(settingsRepository).save(captor.capture());
    assertEquals("hashed-1234", captor.getValue().getPinHash());
  }

  @Test
  void createPin_rejectsWhenAlreadyConfigured() {
    security.when(SecurityUtils::requireUserId).thenReturn(ownerId);
    when(settingsRepository.findById((short) 1)).thenReturn(Optional.empty());

    BusinessException ex = assertThrows(BusinessException.class, () -> service.createPin("1234"));
    assertEquals(MessageConstants.CHAT_DECRYPT_PIN_ALREADY_SET, ex.getMessage());
  }

  @Test
  void resetPinAndClearRoom_wipesThenSavesNewHash() {
    security.when(SecurityUtils::requireUserId).thenReturn(ownerId);
    when(roomRepository.findById(roomId)).thenReturn(Optional.of(ownedRoom()));
    when(settingsRepository.findById((short) 1))
        .thenReturn(
            Optional.of(
                ChatDecryptSettings.builder().id((short) 1).pinHash("old-hash").build()));
    when(passwordEncoder.encode("9999")).thenReturn("new-hash");

    service.resetPinAndClearRoom(roomId, "9999");

    verify(chatService).clearChatHistory(roomId);
    ArgumentCaptor<ChatDecryptSettings> captor = ArgumentCaptor.forClass(ChatDecryptSettings.class);
    verify(settingsRepository).save(captor.capture());
    assertEquals("new-hash", captor.getValue().getPinHash());
  }

  @Test
  void status_reportsConfiguredFromEnv() {
    security.when(SecurityUtils::requireUserId).thenReturn(ownerId);
    when(settingsRepository.findById((short) 1)).thenReturn(Optional.empty());

    assertTrue(service.status().isConfigured());
  }

  @Test
  void status_reportsUnconfiguredWhenNoPin() {
    decryptProperties.setPin("");
    security.when(SecurityUtils::requireUserId).thenReturn(ownerId);
    when(settingsRepository.findById((short) 1)).thenReturn(Optional.empty());

    assertFalse(service.status().isConfigured());
  }

  private ChatRoom ownedRoom() {
    ChatRoom room = ChatRoom.builder().ownerUserId(ownerId).name("notes").build();
    room.setId(roomId);
    return room;
  }
}
