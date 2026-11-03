package com.parentalcontrol.user.service;

import com.parentalcontrol.audit.service.AuditService;
import com.parentalcontrol.common.constant.AuditActionConstants;
import com.parentalcontrol.common.constant.MessageConstants;
import com.parentalcontrol.common.exception.BusinessException;
import com.parentalcontrol.common.exception.ErrorCode;
import com.parentalcontrol.common.security.SecurityUtils;
import com.parentalcontrol.user.dao.UserDao;
import com.parentalcontrol.user.domain.User;
import com.parentalcontrol.user.dto.CameraPasswordStatusResponse;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

/**
 * Camera live-view unlock password. Deliberately separate from the chat decrypt code so opening a
 * camera never reveals the message-decryption credential. Set/changed only via Settings
 * (current + new + confirm); verification is one-shot per camera open.
 */
@Service
@RequiredArgsConstructor
public class CameraPasswordService {

  private static final String DIGIT_PIN = "^\\d{4,8}$";

  private final UserDao userRepository;
  private final PasswordEncoder passwordEncoder;
  private final AuditService auditService;

  @Transactional(readOnly = true)
  public CameraPasswordStatusResponse status() {
    User user = requireParentUser();
    return CameraPasswordStatusResponse.builder().pinConfigured(hasPin(user)).build();
  }

  /** First-time set: new + confirm. Fails if a password already exists. */
  @Transactional
  public void createPin(String pin, String confirmPin) {
    User user = requireParentUser();
    requireMatchingDigits(pin, confirmPin);
    if (hasPin(user)) {
      throw new BusinessException(ErrorCode.CONFLICT, MessageConstants.CAMERA_PASSWORD_ALREADY_SET);
    }
    user.setCameraPasswordHash(passwordEncoder.encode(pin.trim()));
    userRepository.save(user);
    auditService.log(
        AuditActionConstants.CAMERA_PASSWORD_SET, "user:" + user.getId(), null, Map.of());
  }

  /** Change: current → new + confirm. */
  @Transactional
  public void changePin(String currentPin, String pin, String confirmPin) {
    User user = requireParentUser();
    if (!hasPin(user)) {
      throw new BusinessException(ErrorCode.VALIDATION_ERROR, MessageConstants.CAMERA_PASSWORD_NOT_SET);
    }
    if (!pinMatches(user, currentPin)) {
      throw new BusinessException(ErrorCode.FORBIDDEN, MessageConstants.CAMERA_PASSWORD_INVALID);
    }
    requireMatchingDigits(pin, confirmPin);
    user.setCameraPasswordHash(passwordEncoder.encode(pin.trim()));
    userRepository.save(user);
    auditService.log(
        AuditActionConstants.CAMERA_PASSWORD_CHANGED, "user:" + user.getId(), null, Map.of());
  }

  /** One-shot check when opening camera live view. */
  @Transactional(readOnly = true)
  public boolean verifyPin(String pin) {
    User user = requireParentUser();
    if (!hasPin(user)) {
      throw new BusinessException(ErrorCode.VALIDATION_ERROR, MessageConstants.CAMERA_PASSWORD_NOT_SET);
    }
    boolean ok = pinMatches(user, pin);
    if (ok) {
      auditService.log(
          AuditActionConstants.CAMERA_PASSWORD_VERIFIED, "user:" + user.getId(), null, Map.of());
    }
    return ok;
  }

  private static boolean hasPin(User user) {
    return StringUtils.hasText(user.getCameraPasswordHash());
  }

  private boolean pinMatches(User user, String pin) {
    String offered = pin == null ? "" : pin.trim();
    if (!offered.matches(DIGIT_PIN) || !StringUtils.hasText(user.getCameraPasswordHash())) {
      return false;
    }
    return passwordEncoder.matches(offered, user.getCameraPasswordHash());
  }

  private static void requireMatchingDigits(String pin, String confirmPin) {
    String next = pin == null ? "" : pin.trim();
    String confirm = confirmPin == null ? "" : confirmPin.trim();
    if (!next.matches(DIGIT_PIN)) {
      throw new BusinessException(
          ErrorCode.VALIDATION_ERROR, MessageConstants.CAMERA_PASSWORD_MUST_BE_DIGITS);
    }
    if (!next.equals(confirm)) {
      throw new BusinessException(
          ErrorCode.VALIDATION_ERROR, MessageConstants.CAMERA_PASSWORD_MISMATCH);
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
