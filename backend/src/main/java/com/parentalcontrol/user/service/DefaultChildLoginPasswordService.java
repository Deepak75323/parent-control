package com.parentalcontrol.user.service;

import com.parentalcontrol.audit.service.AuditService;
import com.parentalcontrol.common.constant.AuditActionConstants;
import com.parentalcontrol.common.constant.MessageConstants;
import com.parentalcontrol.common.exception.BusinessException;
import com.parentalcontrol.common.exception.ErrorCode;
import com.parentalcontrol.common.security.SecurityUtils;
import com.parentalcontrol.device.dao.DeviceDao;
import com.parentalcontrol.device.domain.Device;
import com.parentalcontrol.user.dao.ParentProfileDao;
import com.parentalcontrol.user.domain.ParentProfile;
import com.parentalcontrol.user.dto.DefaultChildLoginPasswordStatusResponse;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Slf4j
public class DefaultChildLoginPasswordService {

  private final ParentProfileDao parentProfileRepository;
  private final DeviceDao deviceRepository;
  private final PasswordEncoder passwordEncoder;
  private final AuditService auditService;

  @Transactional(readOnly = true)
  public DefaultChildLoginPasswordStatusResponse getStatus() {
    ParentProfile parent = requireParentProfile();
    int deviceCount = (int) deviceRepository.countNonRevokedByParentProfileId(parent.getId());
    return DefaultChildLoginPasswordStatusResponse.builder()
        .configured(isConfigured(parent))
        .linkedDeviceCount(deviceCount)
        .build();
  }

  @Transactional
  public void setPassword(String plaintext) {
    validatePlaintext(plaintext);
    ParentProfile parent = requireParentProfile();
    String hash = passwordEncoder.encode(plaintext.trim());
    parent.setDefaultChildLoginPasswordHash(hash);
    parentProfileRepository.save(parent);
    syncDevices(parent.getId(), hash);
    auditService.log(
        AuditActionConstants.DEFAULT_CHILD_LOGIN_PASSWORD_SET,
        "parentProfile:" + parent.getId(),
        null,
        Map.of(
            "linkedDevices",
            deviceRepository.findByParentProfileIdOrderByCreatedAtDesc(parent.getId()).size()));
    log.info("Default child login password updated for parentProfileId={}", parent.getId());
  }

  /** Hash copied onto every pairing code — one family password only. */
  @Transactional(readOnly = true)
  public String requireHashForPairingCode(ParentProfile parent) {
    if (!isConfigured(parent)) {
      throw new BusinessException(
          ErrorCode.VALIDATION_ERROR,
          MessageConstants
              .SET_YOUR_FAMILY_CHILD_LOGIN_PASSWORD_IN_SETTINGS_BEFORE_GENERATING_A_PAIRING_COD);
    }
    return parent.getDefaultChildLoginPasswordHash();
  }

  /** Ensures a paired device stores the same hash as the parent account. */
  @Transactional
  public void applyFamilyPasswordToDevice(Device device) {
    parentProfileRepository
        .findById(device.getParentProfileId())
        .ifPresent(
            parent -> {
              if (!isConfigured(parent)) {
                return;
              }
              String familyHash = parent.getDefaultChildLoginPasswordHash();
              if (!familyHash.equals(device.getChildLoginPasswordHash())) {
                device.setChildLoginPasswordHash(familyHash);
                deviceRepository.save(device);
              }
            });
  }

  @Transactional(readOnly = true)
  public boolean matchesDefault(ParentProfile parent, String plaintext) {
    if (!isConfigured(parent)) {
      return false;
    }
    return passwordEncoder.matches(plaintext.trim(), parent.getDefaultChildLoginPasswordHash());
  }

  public boolean isConfigured(ParentProfile parent) {
    return parent.getDefaultChildLoginPasswordHash() != null
        && !parent.getDefaultChildLoginPasswordHash().isBlank();
  }

  private void syncDevices(UUID parentProfileId, String hash) {
    deviceRepository
        .findByParentProfileIdOrderByCreatedAtDesc(parentProfileId)
        .forEach(
            device -> {
              device.setChildLoginPasswordHash(hash);
              deviceRepository.save(device);
            });
  }

  private void validatePlaintext(String password) {
    if (password == null || password.isBlank()) {
      throw new BusinessException(
          ErrorCode.VALIDATION_ERROR, MessageConstants.CHILD_LOGIN_PASSWORD_IS_REQUIRED);
    }
    if (password.trim().length() < 4) {
      throw new BusinessException(
          ErrorCode.VALIDATION_ERROR,
          MessageConstants.CHILD_LOGIN_PASSWORD_MUST_BE_AT_LEAST_4_CHARACTERS);
    }
  }

  private ParentProfile requireParentProfile() {
    UUID userId = SecurityUtils.requireUserId();
    return parentProfileRepository
        .findByUser_Id(userId)
        .orElseThrow(
            () ->
                new BusinessException(
                    ErrorCode.NOT_FOUND, MessageConstants.PARENT_PROFILE_NOT_FOUND));
  }
}
