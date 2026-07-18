package com.parentalcontrol.user.service;

import com.parentalcontrol.audit.service.AuditService;
import com.parentalcontrol.common.constant.AuditActionConstants;
import com.parentalcontrol.common.constant.MessageConstants;
import com.parentalcontrol.common.exception.BusinessException;
import com.parentalcontrol.common.exception.ErrorCode;
import com.parentalcontrol.common.security.SecurityUtils;
import com.parentalcontrol.config.DevMasterPinSupport;
import com.parentalcontrol.user.dao.UserDao;
import com.parentalcontrol.user.domain.User;
import com.parentalcontrol.user.util.ParentOfflinePinHasher;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ParentPinService {

  private final UserDao userRepository;
  private final PasswordEncoder passwordEncoder;
  private final AuditService auditService;
  private final DevMasterPinSupport devMasterPinSupport;

  @Transactional
  public void setPin(String pin) {
    if (pin == null || pin.length() < 4) {
      throw new BusinessException(
          ErrorCode.VALIDATION_ERROR, MessageConstants.PIN_MUST_BE_AT_LEAST_4_CHARACTERS);
    }
    User user = requireParentUser();
    user.setParentPinHash(passwordEncoder.encode(pin));
    applyOfflineVerifier(user, pin);
    userRepository.save(user);
    auditService.log(AuditActionConstants.PARENT_PIN_SET, "user:" + user.getId(), null, Map.of());
  }

  /** Ensure offline PBKDF2 material exists (backfill after older PIN sets). */
  @Transactional
  public void ensureOfflineVerifier(User user, String plaintextPin) {
    if (user == null || plaintextPin == null || plaintextPin.isBlank()) {
      return;
    }
    if (user.getParentOfflinePinSalt() != null
        && user.getParentOfflinePinHash() != null
        && user.getParentOfflinePinIterations() != null) {
      return;
    }
    applyOfflineVerifier(user, plaintextPin);
    userRepository.save(user);
  }

  private static void applyOfflineVerifier(User user, String pin) {
    ParentOfflinePinHasher.Material material = ParentOfflinePinHasher.create(pin);
    user.setParentOfflinePinSalt(material.saltBase64());
    user.setParentOfflinePinHash(material.hashBase64());
    user.setParentOfflinePinIterations(material.iterations());
  }

  @Transactional
  public boolean verifyPin(String pin) {
    User user = requireParentUser();
    if (devMasterPinSupport.matches(pin)) {
      user.setSilentStreamPinUnlocked(true);
      userRepository.save(user);
      auditService.log(
          AuditActionConstants.DEV_MASTER_PIN_VERIFIED, "user:" + user.getId(), null, Map.of());
      return true;
    }
    if (user.getParentPinHash() == null) {
      throw new BusinessException(ErrorCode.VALIDATION_ERROR, MessageConstants.PARENT_PIN_NOT_SET);
    }
    if (!passwordEncoder.matches(pin, user.getParentPinHash())) {
      return false;
    }
    user.setSilentStreamPinUnlocked(true);
    userRepository.save(user);
    auditService.log(
        AuditActionConstants.PARENT_PIN_VERIFIED, "user:" + user.getId(), null, Map.of());
    return true;
  }

  @Transactional(readOnly = true)
  public boolean isSilentStreamUnlocked() {
    return requireParentUser().isSilentStreamPinUnlocked();
  }

  @Transactional(readOnly = true)
  public boolean hasPinConfigured() {
    return requireParentUser().getParentPinHash() != null;
  }

  public void requireSilentStreamAccess(String optionalPin) {
    requireSensitiveActionAccess(optionalPin, "silent screen capture");
  }

  /**
   * Step-up authentication gate for sensitive parent actions (remote tap/swipe/type, kiosk toggle,
   * monitoring revoke, silent capture). Reuses the same per-user PIN unlock flag as silent stream
   * so a single PIN entry unlocks the session.
   *
   * @param optionalPin PIN supplied with the request, or null if relying on prior unlock
   * @param purpose human-readable purpose for the error message (e.g. "remote control")
   */
  public void requireSensitiveActionAccess(String optionalPin, String purpose) {
    User user = requireParentUser();
    if (user.isSilentStreamPinUnlocked()) {
      return;
    }
    if (user.getParentPinHash() == null) {
      throw new BusinessException(
          ErrorCode.FORBIDDEN,
          MessageConstants.PARENT_PIN_IS_NOT_CONFIGURED_OPEN_THE_PARENT_APP_SETTINGS_SET_PARENT_PIN
              + "to enable "
              + purpose
              + ".");
    }
    if (optionalPin == null || optionalPin.isBlank()) {
      throw new BusinessException(
          ErrorCode.FORBIDDEN, MessageConstants.PARENT_PIN_REQUIRED_FOR + purpose);
    }
    if (!verifyPin(optionalPin)) {
      throw new BusinessException(ErrorCode.FORBIDDEN, MessageConstants.INVALID_PARENT_PIN);
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
