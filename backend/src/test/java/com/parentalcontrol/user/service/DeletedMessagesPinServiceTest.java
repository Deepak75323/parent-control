package com.parentalcontrol.user.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.parentalcontrol.audit.service.AuditService;
import com.parentalcontrol.chat.repository.ChatMessageRepository;
import com.parentalcontrol.common.exception.BusinessException;
import com.parentalcontrol.common.security.SecurityUtils;
import com.parentalcontrol.user.dao.UserDao;
import com.parentalcontrol.user.domain.User;
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
class DeletedMessagesPinServiceTest {

  @Mock private UserDao userRepository;
  @Mock private PasswordEncoder passwordEncoder;
  @Mock private AuditService auditService;
  @Mock private ChatMessageRepository chatMessageRepository;

  private DeletedMessagesPinService service;
  private MockedStatic<SecurityUtils> security;
  private final UUID userId = UUID.fromString("11111111-1111-1111-1111-111111111111");

  @BeforeEach
  void setUp() {
    service =
        new DeletedMessagesPinService(
            userRepository, passwordEncoder, auditService, chatMessageRepository);
    security = Mockito.mockStatic(SecurityUtils.class);
    security.when(SecurityUtils::requireUserId).thenReturn(userId);
  }

  @AfterEach
  void tearDown() {
    security.close();
  }

  @Test
  void createPinRequiresMatchingDigitsAndRejectsSecondCreate() {
    User user = User.builder().build();
    user.setId(userId);
    when(userRepository.findById(userId)).thenReturn(Optional.of(user));
    when(passwordEncoder.encode("1234")).thenReturn("hash");

    assertThrows(BusinessException.class, () -> service.createPin("1234", "9999"));
    assertThrows(BusinessException.class, () -> service.createPin("12", "12"));

    service.createPin("1234", "1234");
    assertEquals("hash", user.getDeletedMessagesPinHash());
    assertFalse(user.isDeletedMessagesPinUnlocked());

    assertThrows(BusinessException.class, () -> service.createPin("5678", "5678"));
  }

  @Test
  void changePinNeedsCurrentAndLocksAgain() {
    User user = User.builder().deletedMessagesPinHash("old").deletedMessagesPinUnlocked(true).build();
    user.setId(userId);
    when(userRepository.findById(userId)).thenReturn(Optional.of(user));
    when(passwordEncoder.matches("0000", "old")).thenReturn(false);
    when(passwordEncoder.matches("1111", "old")).thenReturn(true);
    when(passwordEncoder.encode("2222")).thenReturn("new");

    assertThrows(BusinessException.class, () -> service.changePin("0000", "2222", "2222"));

    service.changePin("1111", "2222", "2222");
    assertEquals("new", user.getDeletedMessagesPinHash());
    assertFalse(user.isDeletedMessagesPinUnlocked());
  }

  @Test
  void verifyUnlocksAndRequireUnlockedGates() {
    User user = User.builder().deletedMessagesPinHash("hash").build();
    user.setId(userId);
    when(userRepository.findById(userId)).thenReturn(Optional.of(user));
    when(passwordEncoder.matches("1234", "hash")).thenReturn(true);

    assertThrows(BusinessException.class, () -> service.requireUnlockedForDeletedView());
    assertTrue(service.verifyPin("1234"));
    assertTrue(user.isDeletedMessagesPinUnlocked());
    service.requireUnlockedForDeletedView();
  }

  @Test
  void resetClearsDeletedHistoryThenSetsNewPin() {
    User user = User.builder().deletedMessagesPinHash("old").deletedMessagesPinUnlocked(true).build();
    user.setId(userId);
    when(userRepository.findById(userId)).thenReturn(Optional.of(user));
    when(passwordEncoder.encode("9999")).thenReturn("reset");
    when(chatMessageRepository.expireDeletedMessagesInOwnedRooms(userId)).thenReturn(3);

    service.resetAfterClearingDeletedHistory("9999", "9999");

    verify(chatMessageRepository).expireDeletedMessagesInOwnedRooms(eq(userId));
    assertEquals("reset", user.getDeletedMessagesPinHash());
    assertFalse(user.isDeletedMessagesPinUnlocked());
    ArgumentCaptor<User> saved = ArgumentCaptor.forClass(User.class);
    verify(userRepository).save(saved.capture());
    assertEquals("reset", saved.getValue().getDeletedMessagesPinHash());
  }

  @Test
  void resetWithoutExistingPinFailsAndDoesNotPurge() {
    User user = User.builder().build();
    user.setId(userId);
    when(userRepository.findById(userId)).thenReturn(Optional.of(user));

    assertThrows(
        BusinessException.class, () -> service.resetAfterClearingDeletedHistory("1234", "1234"));
    verify(chatMessageRepository, never()).expireDeletedMessagesInOwnedRooms(any());
    verify(passwordEncoder, never()).encode(anyString());
  }
}
