package com.parentalcontrol.user.service;

import com.parentalcontrol.audit.service.AuditService;
import com.parentalcontrol.chat.repository.ChatMessageRepository;
import com.parentalcontrol.common.constant.AuditActionConstants;
import com.parentalcontrol.common.constant.MessageConstants;
import com.parentalcontrol.common.exception.BusinessException;
import com.parentalcontrol.common.exception.ErrorCode;
import com.parentalcontrol.common.security.SecurityUtils;
import com.parentalcontrol.user.dao.UserDao;
import com.parentalcontrol.user.domain.User;
import com.parentalcontrol.user.dto.DeletedMessagesPinStatusResponse;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
@RequiredArgsConstructor
public class DeletedMessagesPinService {

  private static final String DIGIT_PIN = "^\\d{4,8}$";

  private final UserDao userRepository;
  private final PasswordEncoder passwordEncoder;
  private final AuditService auditService;
  private final ChatMessageRepository chatMessageRepository;

  @Transactional(readOnly = true)
  public DeletedMessagesPinStatusResponse status() {
    User user = requireParentUser();
    return DeletedMessagesPinStatusResponse.builder()
        .pinConfigured(hasPin(user))
        .unlocked(user.isDeletedMessagesPinUnlocked())
        .build();
  }

  /** First-time set: new + confirm. Fails if a PIN already exists. */
  @Transactional
  public void createPin(String pin, String confirmPin) {
    User user = requireParentUser();
    requireMatchingDigits(pin, confirmPin);
    if (hasPin(user)) {
      throw new BusinessException(
          ErrorCode.CONFLICT, MessageConstants.DELETED_MESSAGES_PIN_ALREADY_SET);
    }
    user.setDeletedMessagesPinHash(passwordEncoder.encode(pin.trim()));
    user.setDeletedMessagesPinUnlocked(false);
    userRepository.save(user);
    auditService.log(
        AuditActionConstants.DELETED_MESSAGES_PIN_SET, "user:" + user.getId(), null, Map.of());
  }

  /** Change: current → new + confirm. */
  @Transactional
  public void changePin(String currentPin, String pin, String confirmPin) {
    User user = requireParentUser();
    if (!hasPin(user)) {
      throw new BusinessException(
          ErrorCode.VALIDATION_ERROR, MessageConstants.DELETED_MESSAGES_PIN_NOT_SET);
    }
    if (!pinMatches(user, currentPin)) {
      throw new BusinessException(
          ErrorCode.FORBIDDEN, MessageConstants.DELETED_MESSAGES_PIN_INVALID);
    }
    requireMatchingDigits(pin, confirmPin);
    user.setDeletedMessagesPinHash(passwordEncoder.encode(pin.trim()));
    user.setDeletedMessagesPinUnlocked(false);
    userRepository.save(user);
    auditService.log(
        AuditActionConstants.DELETED_MESSAGES_PIN_CHANGED, "user:" + user.getId(), null, Map.of());
  }

  @Transactional
  public boolean verifyPin(String pin) {
    User user = requireParentUser();
    if (!hasPin(user)) {
      throw new BusinessException(
          ErrorCode.VALIDATION_ERROR, MessageConstants.DELETED_MESSAGES_PIN_NOT_SET);
    }
    if (!pinMatches(user, pin)) {
      return false;
    }
    user.setDeletedMessagesPinUnlocked(true);
    userRepository.save(user);
    auditService.log(
        AuditActionConstants.DELETED_MESSAGES_PIN_VERIFIED, "user:" + user.getId(), null, Map.of());
    return true;
  }

  @Transactional
  public void lock() {
    User user = requireParentUser();
    if (!user.isDeletedMessagesPinUnlocked()) {
      return;
    }
    user.setDeletedMessagesPinUnlocked(false);
    userRepository.save(user);
  }

  /**
   * Forgot PIN: clear reviewable deleted notes in rooms this parent owns, then set a new PIN
   * without the old one.
   */
  @Transactional
  public void resetAfterClearingDeletedHistory(String pin, String confirmPin) {
    User user = requireParentUser();
    if (!hasPin(user)) {
      throw new BusinessException(
          ErrorCode.VALIDATION_ERROR, MessageConstants.DELETED_MESSAGES_PIN_NOT_SET);
    }
    requireMatchingDigits(pin, confirmPin);
    chatMessageRepository.expireDeletedMessagesInOwnedRooms(user.getId());
    user.setDeletedMessagesPinHash(passwordEncoder.encode(pin.trim()));
    user.setDeletedMessagesPinUnlocked(false);
    userRepository.save(user);
    auditService.log(
        AuditActionConstants.DELETED_MESSAGES_PIN_RESET, "user:" + user.getId(), null, Map.of());
  }

  @Transactional(readOnly = true)
  public void requireUnlockedForDeletedView() {
    User user = requireParentUser();
    if (!hasPin(user)) {
      throw new BusinessException(
          ErrorCode.FORBIDDEN, MessageConstants.DELETED_MESSAGES_PIN_NOT_SET);
    }
    if (!user.isDeletedMessagesPinUnlocked()) {
      throw new BusinessException(
          ErrorCode.FORBIDDEN, MessageConstants.DELETED_MESSAGES_PIN_REQUIRED);
    }
  }

  private static boolean hasPin(User user) {
    return StringUtils.hasText(user.getDeletedMessagesPinHash());
  }

  private boolean pinMatches(User user, String pin) {
    String offered = pin == null ? "" : pin.trim();
    if (!offered.matches(DIGIT_PIN) || !StringUtils.hasText(user.getDeletedMessagesPinHash())) {
      return false;
    }
    return passwordEncoder.matches(offered, user.getDeletedMessagesPinHash());
  }

  private static void requireMatchingDigits(String pin, String confirmPin) {
    String next = pin == null ? "" : pin.trim();
    String confirm = confirmPin == null ? "" : confirmPin.trim();
    if (!next.matches(DIGIT_PIN)) {
      throw new BusinessException(
          ErrorCode.VALIDATION_ERROR, MessageConstants.DELETED_MESSAGES_PIN_MUST_BE_DIGITS);
    }
    if (!next.equals(confirm)) {
      throw new BusinessException(
          ErrorCode.VALIDATION_ERROR, MessageConstants.DELETED_MESSAGES_PIN_MISMATCH);
    }
  }

  private User requireParentUser() {
    UUID userId = SecurityUtils.requireUserId();
    return userRepository
        .findById(userId)
        .orElseThrow(
            () -> new BusinessException(ErrorCode.NOT_FOUND, MessageConstants.USER_NOT_FOUND));
  }
}
