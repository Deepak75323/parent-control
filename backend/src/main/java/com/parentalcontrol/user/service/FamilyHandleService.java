package com.parentalcontrol.user.service;

import com.parentalcontrol.audit.service.AuditService;
import com.parentalcontrol.common.constant.AuditActionConstants;
import com.parentalcontrol.common.constant.MessageConstants;
import com.parentalcontrol.common.exception.BusinessException;
import com.parentalcontrol.common.exception.ErrorCode;
import com.parentalcontrol.common.security.SecurityUtils;
import com.parentalcontrol.user.dao.ParentProfileDao;
import com.parentalcontrol.user.domain.ParentProfile;
import com.parentalcontrol.user.dto.FamilyHandleStatusResponse;
import com.parentalcontrol.user.util.FamilyHandleNormalizer;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Slf4j
public class FamilyHandleService {

  private final ParentProfileDao parentProfileRepository;
  private final AuditService auditService;

  @Transactional(readOnly = true)
  public FamilyHandleStatusResponse getStatus() {
    ParentProfile parent = requireParentProfile();
    return FamilyHandleStatusResponse.builder()
        .configured(isConfigured(parent))
        .familyHandle(parent.getFamilyHandle())
        .build();
  }

  @Transactional
  public FamilyHandleStatusResponse setHandle(String rawHandle) {
    String normalized = FamilyHandleNormalizer.normalize(rawHandle);
    validateNormalized(normalized);

    ParentProfile parent = requireParentProfile();
    String current = parent.getFamilyHandle();
    if (normalized.equals(current)) {
      return FamilyHandleStatusResponse.builder().configured(true).familyHandle(current).build();
    }

    if (parentProfileRepository.existsByFamilyHandleIgnoreCaseAndIdNot(
        normalized, parent.getId())) {
      throw new BusinessException(ErrorCode.CONFLICT, MessageConstants.FAMILY_HANDLE_ALREADY_TAKEN);
    }

    parent.setFamilyHandle(normalized);
    try {
      parentProfileRepository.save(parent);
      parentProfileRepository.flush();
    } catch (DataIntegrityViolationException e) {
      throw new BusinessException(ErrorCode.CONFLICT, MessageConstants.FAMILY_HANDLE_ALREADY_TAKEN);
    }

    auditService.log(
        AuditActionConstants.FAMILY_HANDLE_SET,
        "parentProfile:" + parent.getId(),
        null,
        Map.of("familyHandle", normalized));
    log.info("Family handle set for parentProfileId={}", parent.getId());

    return FamilyHandleStatusResponse.builder().configured(true).familyHandle(normalized).build();
  }

  @Transactional(readOnly = true)
  public Optional<ParentProfile> findByHandle(String rawHandle) {
    String normalized = FamilyHandleNormalizer.normalize(rawHandle);
    if (normalized == null) {
      return Optional.empty();
    }
    return parentProfileRepository.findByFamilyHandleIgnoreCase(normalized);
  }

  public boolean isConfigured(ParentProfile parent) {
    return parent.getFamilyHandle() != null && !parent.getFamilyHandle().isBlank();
  }

  /** Require handle before generating a pairing code. */
  @Transactional(readOnly = true)
  public void requireConfiguredForPairing(ParentProfile parent) {
    if (!isConfigured(parent)) {
      throw new BusinessException(
          ErrorCode.VALIDATION_ERROR,
          MessageConstants.SET_YOUR_FAMILY_HANDLE_IN_SETTINGS_BEFORE_GENERATING_A_PAIRING_CODE);
    }
  }

  private void validateNormalized(String normalized) {
    if (normalized == null) {
      throw new BusinessException(
          ErrorCode.VALIDATION_ERROR, MessageConstants.FAMILY_HANDLE_IS_REQUIRED);
    }
    if (FamilyHandleNormalizer.isReserved(normalized)) {
      throw new BusinessException(
          ErrorCode.VALIDATION_ERROR, MessageConstants.FAMILY_HANDLE_RESERVED);
    }
    if (!FamilyHandleNormalizer.isValidFormat(normalized)) {
      throw new BusinessException(
          ErrorCode.VALIDATION_ERROR, MessageConstants.FAMILY_HANDLE_INVALID);
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
