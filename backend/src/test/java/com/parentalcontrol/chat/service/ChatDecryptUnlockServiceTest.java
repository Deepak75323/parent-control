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
import com.parentalcontrol.chat.domain.ChatRoomMember;
import com.parentalcontrol.chat.domain.ChatRoomMemberRole;
import com.parentalcontrol.chat.repository.ChatDecryptSettingsRepository;
import com.parentalcontrol.chat.repository.ChatRoomMemberRepository;
import com.parentalcontrol.common.constant.MessageConstants;
import com.parentalcontrol.common.exception.BusinessException;
import com.parentalcontrol.common.security.SecurityUtils;
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
import org.springframework.util.StringUtils;

@ExtendWith(MockitoExtension.class)
class ChatDecryptUnlockServiceTest {

  @Mock private ChatRoomMemberRepository memberRepository;
  @Mock private ChatDecryptSettingsRepository settingsRepository;
  @Mock private PasswordEncoder passwordEncoder;
  @Mock private ChatService chatService;

  private ChatDecryptUnlockService service;
  private MockedStatic<SecurityUtils> security;

  private final UUID ownerId = UUID.fromString("11111111-1111-1111-1111-111111111111");
  private final UUID otherId = UUID.fromString("22222222-2222-2222-2222-222222222222");
  private final UUID roomId = UUID.fromString("33333333-3333-3333-3333-333333333333");
  private static final String SIX_DIGIT_CODE = "987123";

  @BeforeEach
  void setUp() {
    service =
        new ChatDecryptUnlockService(
            memberRepository, settingsRepository, passwordEncoder, chatService);
    security = Mockito.mockStatic(SecurityUtils.class);
  }

  @AfterEach
  void tearDown() {
    security.close();
  }

  @Test
  void unlock_rejectsEnvOnlySecret() {
    security.when(SecurityUtils::requireUserId).thenReturn(ownerId);
    when(memberRepository.findByRoomIdAndUserId(roomId, ownerId))
        .thenReturn(Optional.of(ownerMember()));
    when(settingsRepository.findById((short) 1)).thenReturn(Optional.empty());

    BusinessException ex =
        assertThrows(BusinessException.class, () -> service.unlock(roomId, "4829"));
    assertEquals(MessageConstants.CHAT_DECRYPT_NOT_CONFIGURED, ex.getMessage());
    verify(settingsRepository, never()).save(any(ChatDecryptSettings.class));
  }

  @Test
  void unlock_succeedsForGrantedMemberWithDbHash() {
    security.when(SecurityUtils::requireUserId).thenReturn(otherId);
    ChatRoomMember granted = member(otherId, ChatRoomMemberRole.MEMBER);
    granted.setCanDecrypt(true);
    when(memberRepository.findByRoomIdAndUserId(roomId, otherId))
        .thenReturn(Optional.of(granted));
    when(settingsRepository.findById((short) 1))
        .thenReturn(
            Optional.of(
                ChatDecryptSettings.builder()
                    .id((short) 1)
                    .pinHash("hashed-987123")
                    .cryptoSalt("c2FsdA==")
                    .build()));
    when(passwordEncoder.matches(SIX_DIGIT_CODE, "hashed-987123")).thenReturn(true);

    assertDoesNotThrow(() -> service.unlock(roomId, SIX_DIGIT_CODE));
  }

  @Test
  void unlock_rejectsMemberWithoutGrant() {
    security.when(SecurityUtils::requireUserId).thenReturn(otherId);
    when(memberRepository.findByRoomIdAndUserId(roomId, otherId))
        .thenReturn(Optional.of(member(otherId, ChatRoomMemberRole.MEMBER)));

    BusinessException ex =
        assertThrows(BusinessException.class, () -> service.unlock(roomId, "4829"));
    assertEquals(MessageConstants.CHAT_ONLY_OWNER_CAN_DECRYPT, ex.getMessage());
  }

  @Test
  void unlock_rejectsWrongPin() {
    security.when(SecurityUtils::requireUserId).thenReturn(ownerId);
    when(memberRepository.findByRoomIdAndUserId(roomId, ownerId))
        .thenReturn(Optional.of(ownerMember()));
    when(settingsRepository.findById((short) 1))
        .thenReturn(
            Optional.of(
                ChatDecryptSettings.builder()
                    .id((short) 1)
                    .pinHash("hashed-987123")
                    .cryptoSalt("c2FsdA==")
                    .build()));
    when(passwordEncoder.matches("000000", "hashed-987123")).thenReturn(false);

    BusinessException ex =
        assertThrows(BusinessException.class, () -> service.unlock(roomId, "000000"));
    assertEquals(MessageConstants.CHAT_DECRYPT_PIN_INVALID, ex.getMessage());
  }

  @Test
  void unlock_rejectsWhenPinNotConfigured() {
    security.when(SecurityUtils::requireUserId).thenReturn(ownerId);
    when(memberRepository.findByRoomIdAndUserId(roomId, ownerId))
        .thenReturn(Optional.of(ownerMember()));
    when(settingsRepository.findById((short) 1)).thenReturn(Optional.empty());

    BusinessException ex =
        assertThrows(BusinessException.class, () -> service.unlock(roomId, "4829"));
    assertEquals(MessageConstants.CHAT_DECRYPT_NOT_CONFIGURED, ex.getMessage());
  }

  @Test
  void createPin_persistsHashWhenUnset() {
    security.when(SecurityUtils::requireUserId).thenReturn(ownerId);
    when(settingsRepository.findById((short) 1)).thenReturn(Optional.empty());
    when(passwordEncoder.encode(SIX_DIGIT_CODE)).thenReturn("hashed-secret");
    when(settingsRepository.save(any(ChatDecryptSettings.class)))
        .thenAnswer(invocation -> invocation.getArgument(0));

    service.createPin(SIX_DIGIT_CODE);

    ArgumentCaptor<ChatDecryptSettings> captor = ArgumentCaptor.forClass(ChatDecryptSettings.class);
    verify(settingsRepository).save(captor.capture());
    assertEquals("hashed-secret", captor.getValue().getPinHash());
    assertTrue(StringUtils.hasText(captor.getValue().getCryptoSalt()));
  }

  @Test
  void createPin_rejectsWhenAlreadyConfigured() {
    security.when(SecurityUtils::requireUserId).thenReturn(ownerId);
    when(settingsRepository.findById((short) 1))
        .thenReturn(
            Optional.of(
                ChatDecryptSettings.builder().id((short) 1).pinHash("hashed-secret").build()));

    BusinessException ex =
        assertThrows(BusinessException.class, () -> service.createPin(SIX_DIGIT_CODE));
    assertEquals(MessageConstants.CHAT_DECRYPT_PIN_ALREADY_SET, ex.getMessage());
  }

  @Test
  void createPin_rejectsShortPassphrase() {
    security.when(SecurityUtils::requireUserId).thenReturn(ownerId);

    BusinessException ex = assertThrows(BusinessException.class, () -> service.createPin("1234"));
    assertEquals(MessageConstants.CHAT_DECRYPT_PIN_INVALID, ex.getMessage());
  }

  @Test
  void resetPinAndClearRoom_wipesThenSavesNewHash() {
    security.when(SecurityUtils::requireUserId).thenReturn(ownerId);
    when(memberRepository.findByRoomIdAndUserId(roomId, ownerId))
        .thenReturn(Optional.of(ownerMember()));
    when(settingsRepository.findById((short) 1))
        .thenReturn(
            Optional.of(
                ChatDecryptSettings.builder().id((short) 1).pinHash("old-hash").build()));
    when(passwordEncoder.encode(SIX_DIGIT_CODE)).thenReturn("new-hash");
    when(settingsRepository.save(any(ChatDecryptSettings.class)))
        .thenAnswer(invocation -> invocation.getArgument(0));

    service.resetPinAndClearRoom(roomId, SIX_DIGIT_CODE);

    verify(chatService).clearChatHistoryAsSystem(roomId);
    ArgumentCaptor<ChatDecryptSettings> captor = ArgumentCaptor.forClass(ChatDecryptSettings.class);
    verify(settingsRepository).save(captor.capture());
    assertEquals("new-hash", captor.getValue().getPinHash());
  }

  @Test
  void verifyPin_succeedsWithDbHash() {
    security.when(SecurityUtils::requireUserId).thenReturn(ownerId);
    when(settingsRepository.findById((short) 1))
        .thenReturn(
            Optional.of(
                ChatDecryptSettings.builder().id((short) 1).pinHash("hashed-secret").build()));
    when(passwordEncoder.matches(SIX_DIGIT_CODE, "hashed-secret")).thenReturn(true);

    assertDoesNotThrow(() -> service.verifyPin(SIX_DIGIT_CODE));
  }

  @Test
  void verifyPin_rejectsWrongPassphrase() {
    security.when(SecurityUtils::requireUserId).thenReturn(ownerId);
    when(settingsRepository.findById((short) 1))
        .thenReturn(
            Optional.of(
                ChatDecryptSettings.builder().id((short) 1).pinHash("hashed-secret").build()));
    when(passwordEncoder.matches("000000", "hashed-secret")).thenReturn(false);

    BusinessException ex =
        assertThrows(BusinessException.class, () -> service.verifyPin("000000"));
    assertEquals(MessageConstants.CHAT_DECRYPT_PIN_INVALID, ex.getMessage());
  }

  @Test
  void status_reportsUnconfiguredWhenNoDatabaseHash() {
    security.when(SecurityUtils::requireUserId).thenReturn(ownerId);
    when(settingsRepository.findById((short) 1)).thenReturn(Optional.empty());

    assertFalse(service.status().isConfigured());
  }

  private ChatRoomMember ownerMember() {
    return member(ownerId, ChatRoomMemberRole.OWNER);
  }

  private ChatRoomMember member(UUID userId, ChatRoomMemberRole role) {
    return ChatRoomMember.builder().roomId(roomId).userId(userId).role(role).build();
  }
}
