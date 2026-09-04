package com.parentalcontrol.chat.service;

import com.parentalcontrol.chat.domain.ChatRoom;
import com.parentalcontrol.chat.repository.ChatRoomRepository;
import com.parentalcontrol.common.constant.MessageConstants;
import com.parentalcontrol.common.exception.BusinessException;
import com.parentalcontrol.common.exception.ErrorCode;
import com.parentalcontrol.common.security.SecurityUtils;
import com.parentalcontrol.config.ChatDecryptProperties;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ChatDecryptUnlockService {

  private final ChatRoomRepository roomRepository;
  private final ChatDecryptProperties decryptProperties;

  /**
   * Verifies the room owner and the server decrypt PIN. Does not return message plaintext — the
   * client decodes locally after a successful unlock.
   */
  @Transactional(readOnly = true)
  public void unlock(UUID roomId, String pin) {
    UUID userId = SecurityUtils.requireUserId();
    if (!decryptProperties.isConfigured()) {
      throw new BusinessException(
          ErrorCode.VALIDATION_ERROR, MessageConstants.CHAT_DECRYPT_NOT_CONFIGURED);
    }

    ChatRoom room =
        roomRepository
            .findById(roomId)
            .orElseThrow(
                () ->
                    new BusinessException(
                        ErrorCode.NOT_FOUND, MessageConstants.CHAT_ROOM_NOT_FOUND));
    if (!room.getOwnerUserId().equals(userId)) {
      throw new BusinessException(
          ErrorCode.FORBIDDEN, MessageConstants.CHAT_ONLY_OWNER_CAN_DECRYPT);
    }

    String offered = pin == null ? "" : pin.trim();
    byte[] expected = decryptProperties.configuredPin().getBytes(StandardCharsets.UTF_8);
    byte[] actual = offered.getBytes(StandardCharsets.UTF_8);
    if (!MessageDigest.isEqual(expected, actual)) {
      throw new BusinessException(ErrorCode.FORBIDDEN, MessageConstants.CHAT_DECRYPT_PIN_INVALID);
    }
  }
}
