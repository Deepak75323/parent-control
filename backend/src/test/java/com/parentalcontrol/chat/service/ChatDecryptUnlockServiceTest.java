package com.parentalcontrol.chat.service;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.when;

import com.parentalcontrol.chat.domain.ChatRoom;
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
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ChatDecryptUnlockServiceTest {

  @Mock private ChatRoomRepository roomRepository;

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
    service = new ChatDecryptUnlockService(roomRepository, decryptProperties);
    security = Mockito.mockStatic(SecurityUtils.class);
  }

  @AfterEach
  void tearDown() {
    security.close();
  }

  @Test
  void unlock_succeedsForOwnerWithCorrectPin() {
    security.when(SecurityUtils::requireUserId).thenReturn(ownerId);
    when(roomRepository.findById(roomId)).thenReturn(Optional.of(ownedRoom()));

    assertDoesNotThrow(() -> service.unlock(roomId, "4829"));
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

    BusinessException ex =
        assertThrows(BusinessException.class, () -> service.unlock(roomId, "0000"));
    assertEquals(MessageConstants.CHAT_DECRYPT_PIN_INVALID, ex.getMessage());
  }

  @Test
  void unlock_rejectsWhenPinNotConfigured() {
    decryptProperties.setPin("");
    security.when(SecurityUtils::requireUserId).thenReturn(ownerId);

    BusinessException ex =
        assertThrows(BusinessException.class, () -> service.unlock(roomId, "4829"));
    assertEquals(MessageConstants.CHAT_DECRYPT_NOT_CONFIGURED, ex.getMessage());
  }

  private ChatRoom ownedRoom() {
    ChatRoom room = ChatRoom.builder().ownerUserId(ownerId).name("notes").build();
    room.setId(roomId);
    return room;
  }
}
