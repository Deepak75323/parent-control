package com.parentalcontrol.chat.service;

import com.parentalcontrol.chat.domain.ChatDecryptSettings;
import com.parentalcontrol.chat.domain.ChatRoomMember;
import com.parentalcontrol.chat.domain.ChatRoomMemberRole;
import com.parentalcontrol.chat.dto.ChatDecryptStatusResponse;
import com.parentalcontrol.chat.repository.ChatDecryptSettingsRepository;
import com.parentalcontrol.chat.repository.ChatRoomMemberRepository;
import com.parentalcontrol.common.constant.MessageConstants;
import com.parentalcontrol.common.exception.BusinessException;
import com.parentalcontrol.common.exception.ErrorCode;
import com.parentalcontrol.common.security.SecurityUtils;
import com.parentalcontrol.config.ChatDecryptProperties;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
@RequiredArgsConstructor
public class ChatDecryptUnlockService {

  private static final short SETTINGS_ID = 1;

  private final ChatRoomMemberRepository memberRepository;
  private final ChatDecryptSettingsRepository settingsRepository;
  private final ChatDecryptProperties decryptProperties;
  private final PasswordEncoder passwordEncoder;
  private final ChatService chatService;

  @Transactional(readOnly = true)
  public ChatDecryptStatusResponse status() {
    SecurityUtils.requireUserId();
    return ChatDecryptStatusResponse.builder().configured(isConfigured()).build();
  }

  /**
   * First-time PIN create (portal). Allowed for any authenticated parent when no PIN exists yet
   * (DB hash or env fallback).
   */
  @Transactional
  public void createPin(String pin) {
    SecurityUtils.requireUserId();
    requireFourDigit(pin);
    if (isConfigured()) {
      throw new BusinessException(
          ErrorCode.CONFLICT, MessageConstants.CHAT_DECRYPT_PIN_ALREADY_SET);
    }
    saveHash(pin.trim());
  }

  /**
   * Verifies decrypt permission (owner or granted) and the server decrypt PIN. Does not return
   * message plaintext — the client decodes locally after a successful unlock.
   */
  @Transactional
  public void unlock(UUID roomId, String pin) {
    UUID userId = SecurityUtils.requireUserId();
    requireDecryptPermission(roomId, userId);
    if (!isConfigured()) {
      throw new BusinessException(
          ErrorCode.VALIDATION_ERROR, MessageConstants.CHAT_DECRYPT_NOT_CONFIGURED);
    }
    if (!pinMatches(pin)) {
      throw new BusinessException(ErrorCode.FORBIDDEN, MessageConstants.CHAT_DECRYPT_PIN_INVALID);
    }
    // Lazy-migrate env PIN into DB hash so portal create/reset works going forward.
    if (settingsRepository.findById(SETTINGS_ID).isEmpty() && decryptProperties.isConfigured()) {
      saveHash(decryptProperties.configuredPin());
    }
  }

  /**
   * Snapchat-style forgot: wipe this room's messages for everyone, then set a new server PIN.
   * Does not require the old PIN. Owner only.
   */
  @Transactional
  public void resetPinAndClearRoom(UUID roomId, String newPin) {
    UUID userId = SecurityUtils.requireUserId();
    requireRoomOwnerMembership(roomId, userId);
    requireFourDigit(newPin);
    if (!isConfigured()) {
      throw new BusinessException(
          ErrorCode.VALIDATION_ERROR, MessageConstants.CHAT_DECRYPT_NOT_CONFIGURED);
    }
    chatService.clearChatHistoryAsSystem(roomId);
    saveHash(newPin.trim());
  }

  boolean isConfigured() {
    if (settingsRepository.findById(SETTINGS_ID).map(ChatDecryptSettings::getPinHash).isPresent()) {
      return true;
    }
    return decryptProperties.isConfigured();
  }

  private boolean pinMatches(String pin) {
    String offered = pin == null ? "" : pin.trim();
    return settingsRepository
        .findById(SETTINGS_ID)
        .map(settings -> passwordEncoder.matches(offered, settings.getPinHash()))
        .orElseGet(
            () -> {
              if (!decryptProperties.isConfigured()) {
                return false;
              }
              byte[] expected =
                  decryptProperties.configuredPin().getBytes(StandardCharsets.UTF_8);
              byte[] actual = offered.getBytes(StandardCharsets.UTF_8);
              return MessageDigest.isEqual(expected, actual);
            });
  }

  private void saveHash(String pin) {
    ChatDecryptSettings settings =
        settingsRepository
            .findById(SETTINGS_ID)
            .orElseGet(() -> ChatDecryptSettings.builder().id(SETTINGS_ID).build());
    settings.setPinHash(passwordEncoder.encode(pin));
    settingsRepository.save(settings);
  }

  private void requireDecryptPermission(UUID roomId, UUID userId) {
    ChatRoomMember member =
        memberRepository
            .findByRoomIdAndUserId(roomId, userId)
            .orElseThrow(
                () ->
                    new BusinessException(
                        ErrorCode.FORBIDDEN, MessageConstants.CHAT_NOT_A_MEMBER));
    if (!ChatRoomModerationRules.canDecrypt(member)) {
      throw new BusinessException(
          ErrorCode.FORBIDDEN, MessageConstants.CHAT_ONLY_OWNER_CAN_DECRYPT);
    }
  }

  private void requireRoomOwnerMembership(UUID roomId, UUID userId) {
    ChatRoomMember member =
        memberRepository
            .findByRoomIdAndUserId(roomId, userId)
            .orElseThrow(
                () ->
                    new BusinessException(
                        ErrorCode.FORBIDDEN, MessageConstants.CHAT_NOT_A_MEMBER));
    if (member.getRole() != ChatRoomMemberRole.OWNER) {
      throw new BusinessException(
          ErrorCode.FORBIDDEN, MessageConstants.CHAT_ONLY_OWNER_CAN_DECRYPT);
    }
  }

  private static void requireFourDigit(String pin) {
    if (!StringUtils.hasText(pin) || !pin.trim().matches("^\\d{4}$")) {
      throw new BusinessException(
          ErrorCode.VALIDATION_ERROR, MessageConstants.CHAT_DECRYPT_PIN_INVALID);
    }
  }
}
