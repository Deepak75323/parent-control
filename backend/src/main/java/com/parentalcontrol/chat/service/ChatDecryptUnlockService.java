package com.parentalcontrol.chat.service;

import com.parentalcontrol.chat.domain.ChatDecryptSettings;
import com.parentalcontrol.chat.domain.ChatRoomMember;
import com.parentalcontrol.chat.domain.ChatRoomMemberRole;
import com.parentalcontrol.chat.dto.ChatDecryptStatusResponse;
import com.parentalcontrol.chat.dto.ChatDecryptUnlockResponse;
import com.parentalcontrol.chat.repository.ChatDecryptSettingsRepository;
import com.parentalcontrol.chat.repository.ChatRoomMemberRepository;
import com.parentalcontrol.common.constant.MessageConstants;
import com.parentalcontrol.common.exception.BusinessException;
import com.parentalcontrol.common.exception.ErrorCode;
import com.parentalcontrol.common.security.SecurityUtils;
import java.security.SecureRandom;
import java.util.Base64;
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
  private static final int SALT_BYTES = 16;
  private static final String SIX_DIGIT_CODE_PATTERN = "^\\d{6}$";

  private final ChatRoomMemberRepository memberRepository;
  private final ChatDecryptSettingsRepository settingsRepository;
  private final PasswordEncoder passwordEncoder;
  private final ChatService chatService;

  @Transactional
  public ChatDecryptStatusResponse status() {
    SecurityUtils.requireUserId();
    boolean configured = isConfigured();
    return ChatDecryptStatusResponse.builder()
        .configured(configured)
        .cryptoSalt(configured ? ensureSalt().getCryptoSalt() : null)
        .build();
  }

  /** First-time 6-digit code creation by a parent in the portal. */
  @Transactional
  public ChatDecryptUnlockResponse createPin(String pin) {
    SecurityUtils.requireUserId();
    requireStrongPassphrase(pin);
    if (isConfigured()) {
      throw new BusinessException(
          ErrorCode.CONFLICT, MessageConstants.CHAT_DECRYPT_PIN_ALREADY_SET);
    }
    ChatDecryptSettings settings = saveHash(pin.trim());
    return ChatDecryptUnlockResponse.builder().cryptoSalt(settings.getCryptoSalt()).build();
  }

  /**
   * Verifies decrypt permission (owner or granted) and the server decrypt passphrase. Returns the
   * public crypto salt so the client can derive the AES key locally — plaintext is never returned.
   */
  @Transactional
  public ChatDecryptUnlockResponse unlock(UUID roomId, String pin) {
    UUID userId = SecurityUtils.requireUserId();
    requireDecryptPermission(roomId, userId);
    if (!isConfigured()) {
      throw new BusinessException(
          ErrorCode.VALIDATION_ERROR, MessageConstants.CHAT_DECRYPT_NOT_CONFIGURED);
    }
    if (!pinMatches(pin)) {
      throw new BusinessException(ErrorCode.FORBIDDEN, MessageConstants.CHAT_DECRYPT_PIN_INVALID);
    }
    ChatDecryptSettings settings = ensureSalt();
    return ChatDecryptUnlockResponse.builder().cryptoSalt(settings.getCryptoSalt()).build();
  }

  /** Verify passphrase only (camera unlock fallback). No room permission check. */
  @Transactional(readOnly = true)
  public void verifyPin(String pin) {
    SecurityUtils.requireUserId();
    if (!isConfigured()) {
      throw new BusinessException(
          ErrorCode.VALIDATION_ERROR, MessageConstants.CHAT_DECRYPT_NOT_CONFIGURED);
    }
    if (!pinMatches(pin)) {
      throw new BusinessException(ErrorCode.FORBIDDEN, MessageConstants.CHAT_DECRYPT_PIN_INVALID);
    }
  }

  /**
   * Snapchat-style forgot: wipe this room's messages for everyone, then set a new server passphrase.
   * Does not require the old passphrase. Owner only.
   */
  @Transactional
  public ChatDecryptUnlockResponse resetPinAndClearRoom(UUID roomId, String newPin) {
    UUID userId = SecurityUtils.requireUserId();
    requireRoomOwnerMembership(roomId, userId);
    requireStrongPassphrase(newPin);
    if (!isConfigured()) {
      throw new BusinessException(
          ErrorCode.VALIDATION_ERROR, MessageConstants.CHAT_DECRYPT_NOT_CONFIGURED);
    }
    chatService.clearChatHistoryAsSystem(roomId);
    ChatDecryptSettings settings = saveHash(newPin.trim());
    return ChatDecryptUnlockResponse.builder().cryptoSalt(settings.getCryptoSalt()).build();
  }

  boolean isConfigured() {
    return settingsRepository
        .findById(SETTINGS_ID)
        .map(ChatDecryptSettings::getPinHash)
        .filter(StringUtils::hasText)
        .isPresent();
  }

  private boolean pinMatches(String pin) {
    String offered = pin == null ? "" : pin.trim();
    if (!offered.matches(SIX_DIGIT_CODE_PATTERN)) {
      return false;
    }
    return settingsRepository
        .findById(SETTINGS_ID)
        .filter(settings -> StringUtils.hasText(settings.getPinHash()))
        .map(settings -> passwordEncoder.matches(offered, settings.getPinHash()))
        .orElse(false);
  }

  private ChatDecryptSettings saveHash(String pin) {
    ChatDecryptSettings settings =
        settingsRepository
            .findById(SETTINGS_ID)
            .orElseGet(() -> ChatDecryptSettings.builder().id(SETTINGS_ID).build());
    settings.setPinHash(passwordEncoder.encode(pin));
    if (!StringUtils.hasText(settings.getCryptoSalt())) {
      settings.setCryptoSalt(newSaltBase64());
    }
    return settingsRepository.save(settings);
  }

  private ChatDecryptSettings ensureSalt() {
    ChatDecryptSettings settings =
        settingsRepository
            .findById(SETTINGS_ID)
            .orElseThrow(
                () ->
                    new BusinessException(
                        ErrorCode.VALIDATION_ERROR, MessageConstants.CHAT_DECRYPT_NOT_CONFIGURED));
    if (!StringUtils.hasText(settings.getPinHash())) {
      throw new BusinessException(
          ErrorCode.VALIDATION_ERROR, MessageConstants.CHAT_DECRYPT_NOT_CONFIGURED);
    }
    if (!StringUtils.hasText(settings.getCryptoSalt())) {
      settings.setCryptoSalt(newSaltBase64());
      return settingsRepository.save(settings);
    }
    return settings;
  }

  private static String newSaltBase64() {
    byte[] salt = new byte[SALT_BYTES];
    new SecureRandom().nextBytes(salt);
    return Base64.getEncoder().encodeToString(salt);
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

  private static void requireStrongPassphrase(String pin) {
    if (!StringUtils.hasText(pin)) {
      throw new BusinessException(
          ErrorCode.VALIDATION_ERROR, MessageConstants.CHAT_DECRYPT_PIN_INVALID);
    }
    String trimmed = pin.trim();
    if (!trimmed.matches(SIX_DIGIT_CODE_PATTERN)) {
      throw new BusinessException(
          ErrorCode.VALIDATION_ERROR, MessageConstants.CHAT_DECRYPT_PIN_INVALID);
    }
  }
}
