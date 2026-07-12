package com.parentalcontrol.device.service;

import com.parentalcontrol.audit.service.AuditService;
import com.parentalcontrol.common.constant.AuditActionConstants;
import com.parentalcontrol.common.constant.MessageConstants;
import com.parentalcontrol.common.constant.TimeoutConstants;
import com.parentalcontrol.common.exception.BusinessException;
import com.parentalcontrol.common.exception.ErrorCode;
import com.parentalcontrol.config.DevMasterPinSupport;
import com.parentalcontrol.device.dao.DeviceDao;
import com.parentalcontrol.device.dao.PairingCodeDao;
import com.parentalcontrol.device.dao.TamperUnlockCodeDao;
import com.parentalcontrol.device.domain.Device;
import com.parentalcontrol.device.domain.PairingCode;
import com.parentalcontrol.device.domain.TamperUnlockCode;
import com.parentalcontrol.device.dto.OnboardingAuthRequest;
import com.parentalcontrol.device.dto.OnboardingAuthResponse;
import com.parentalcontrol.device.dto.OnboardingRecoveryRequest;
import com.parentalcontrol.device.dto.OnboardingRecoveryResponse;
import com.parentalcontrol.monitoring.service.AlertService;
import com.parentalcontrol.user.dao.ParentProfileDao;
import com.parentalcontrol.user.domain.ParentProfile;
import com.parentalcontrol.user.service.DefaultChildLoginPasswordService;
import java.security.SecureRandom;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
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
public class OnboardingAuthService {

  private static final int RECOVERY_CODE_TTL_MINUTES = TimeoutConstants.RECOVERY_CODE_TTL_MINUTES;

  private final PairingCodeDao pairingCodeRepository;
  private final DeviceDao deviceRepository;
  private final TamperUnlockCodeDao tamperUnlockCodeRepository;
  private final ParentProfileDao parentProfileRepository;
  private final DefaultChildLoginPasswordService defaultChildLoginPasswordService;
  private final PasswordEncoder passwordEncoder;
  private final DevMasterPinSupport devMasterPinSupport;
  private final AuditService auditService;
  private final AlertService alertService;
  private final SecureRandom secureRandom = new SecureRandom();

  @Transactional
  public OnboardingAuthResponse authenticate(OnboardingAuthRequest request) {
    if (request.getRecoveryCode() != null && !request.getRecoveryCode().isBlank()) {
      return authenticateRecoveryCode(request);
    }
    if (request.isSetupCodeOnly()) {
      return authenticateSetupCode(request);
    }
    return authenticatePassword(request);
  }

  private static final String RECOVERY_GENERIC_MESSAGE =
      MessageConstants.IF_THIS_DEVICE_IS_PAIRED_A_RECOVERY_CODE_HAS_BEEN_SENT_TO_THE_PARENT_APP;

  @Transactional
  public OnboardingRecoveryResponse requestRecoveryCode(OnboardingRecoveryRequest request) {
    String androidId = request.getAndroidId().trim();
    var deviceOpt = deviceRepository.findByAndroidId(androidId);
    if (deviceOpt.isPresent()) {
      Device device = deviceOpt.get();
      TamperUnlockCode activeCode = ensureRecoveryCode(device);
      alertService.checkChildLoginRecovery(device, activeCode.getCode(), activeCode.getExpiresAt());
      auditService.log(
          AuditActionConstants.ONBOARDING_RECOVERY_REQUEST,
          "device:" + device.getId(),
          device.getId(),
          Map.of());
    }

    return OnboardingRecoveryResponse.builder()
        .codeSent(true)
        .message(RECOVERY_GENERIC_MESSAGE)
        .build();
  }

  private OnboardingAuthResponse authenticateRecoveryCode(OnboardingAuthRequest request) {
    String androidId = request.getAndroidId();
    if (androidId == null || androidId.isBlank()) {
      throw new BusinessException(
          ErrorCode.VALIDATION_ERROR, MessageConstants.DEVICE_ID_IS_REQUIRED_FOR_RECOVERY);
    }

    Device device =
        deviceRepository
            .findByAndroidId(androidId.trim())
            .orElseThrow(
                () ->
                    new BusinessException(ErrorCode.NOT_FOUND, MessageConstants.DEVICE_NOT_FOUND));

    String rawCode = request.getRecoveryCode().trim();
    TamperUnlockCode code =
        tamperUnlockCodeRepository
            .findFirstByDeviceIdAndUsedAtIsNullAndExpiresAtAfterOrderByCreatedAtDesc(
                device.getId(), Instant.now())
            .orElseThrow(
                () ->
                    new BusinessException(
                        ErrorCode.FORBIDDEN,
                        MessageConstants
                            .NO_ACTIVE_RECOVERY_CODE_TAP_FORGOT_PASSWORD_AGAIN_TO_REQUEST_A_NEW_CODE));

    if (!code.getCode().equalsIgnoreCase(rawCode)) {
      auditService.log(
          AuditActionConstants.ONBOARDING_AUTH_FAILURE,
          "device:" + device.getId(),
          null,
          Map.of("method", "recovery_code"));
      return OnboardingAuthResponse.builder().authenticated(false).build();
    }

    code.setUsedAt(Instant.now());
    tamperUnlockCodeRepository.save(code);

    auditService.log(
        AuditActionConstants.ONBOARDING_AUTH_RECOVERY_CODE,
        "device:" + device.getId(),
        device.getId(),
        Map.of());

    return OnboardingAuthResponse.builder().authenticated(true).build();
  }

  private OnboardingAuthResponse authenticateSetupCode(OnboardingAuthRequest request) {
    if (request.getPairingCode() == null || request.getPairingCode().isBlank()) {
      throw new BusinessException(
          ErrorCode.VALIDATION_ERROR, MessageConstants.SETUP_CODE_IS_REQUIRED);
    }

    PairingCode pairingCode =
        pairingCodeRepository
            .findByCodeIgnoreCase(request.getPairingCode().trim())
            .orElseThrow(
                () ->
                    new BusinessException(
                        ErrorCode.NOT_FOUND, MessageConstants.INVALID_SETUP_CODE));

    if (pairingCode.isUsed()) {
      throw new BusinessException(ErrorCode.FORBIDDEN, MessageConstants.SETUP_CODE_ALREADY_USED);
    }
    if (pairingCode.isExpired()) {
      throw new BusinessException(ErrorCode.FORBIDDEN, MessageConstants.SETUP_CODE_EXPIRED);
    }

    auditService.log(
        AuditActionConstants.ONBOARDING_AUTH_SETUP_CODE,
        "parentProfile:" + pairingCode.getParentProfileId(),
        null,
        Map.of("pairingCode", redact(pairingCode.getCode())));

    return OnboardingAuthResponse.builder().authenticated(true).build();
  }

  private OnboardingAuthResponse authenticatePassword(OnboardingAuthRequest request) {
    String password = request.getChildLoginPassword();
    if (password == null || password.isBlank()) {
      throw new BusinessException(
          ErrorCode.VALIDATION_ERROR, MessageConstants.CHILD_LOGIN_PASSWORD_IS_REQUIRED);
    }
    String trimmed = password.trim();

    if (devMasterPinSupport.matches(trimmed)) {
      auditService.log(AuditActionConstants.ONBOARDING_AUTH_MASTER_PIN, "system", null, Map.of());
      return OnboardingAuthResponse.builder().authenticated(true).build();
    }

    String androidId =
        request.getAndroidId() != null ? request.getAndroidId().trim().toLowerCase() : null;
    if (androidId != null && !androidId.isBlank()) {
      var deviceOpt = deviceRepository.findByAndroidId(androidId);
      if (deviceOpt.isPresent()) {
        return authenticateAgainstDevice(deviceOpt.get(), trimmed);
      }
      // Client sent an androidId that does not match any paired device (OEM restore,
      // profile mismatch, or stale read). Fall back to unique device/password match.
      OnboardingAuthResponse unknownId = authenticateWhenAndroidIdMissing(trimmed);
      if (unknownId.isAuthenticated()) {
        return unknownId;
      }
    } else {
      // Client may omit androidId (native bridge timing). After pairing, active setup
      // codes are consumed — fall back to family/device password for a unique match.
      OnboardingAuthResponse withoutId = authenticateWhenAndroidIdMissing(trimmed);
      if (withoutId.isAuthenticated()) {
        return withoutId;
      }
    }

    return authenticateAgainstActivePairingCode(trimmed, null);
  }

  /**
   * Authenticate when the child app did not send androidId. Only succeeds when the password matches
   * exactly one paired device (or one parent's family password with a single linked device) to
   * avoid ambiguous cross-family login.
   */
  private OnboardingAuthResponse authenticateWhenAndroidIdMissing(String password) {
    List<Device> directMatches = new ArrayList<>();
    for (Device device : deviceRepository.findAllActive()) {
      if (device.getChildLoginPasswordHash() != null
          && passwordEncoder.matches(password, device.getChildLoginPasswordHash())) {
        directMatches.add(device);
      }
    }
    if (directMatches.size() == 1) {
      auditService.log(
          AuditActionConstants.ONBOARDING_AUTH_CHILD_PASSWORD,
          "device:" + directMatches.get(0).getId(),
          null,
          Map.of("source", "missing_android_id"));
      return OnboardingAuthResponse.builder().authenticated(true).build();
    }
    if (directMatches.size() > 1) {
      throw new BusinessException(
          ErrorCode.FORBIDDEN,
          MessageConstants.MULTIPLE_DEVICES_MATCH_THIS_PASSWORD_RE_OPEN_THE_APP_AND_TRY_AGAIN);
    }

    List<Device> familyMatches = new ArrayList<>();
    for (ParentProfile parent : parentProfileRepository.findAll()) {
      if (!defaultChildLoginPasswordService.matchesDefault(parent, password)) {
        continue;
      }
      List<Device> devices =
          deviceRepository.findActiveByParentProfileIdOrderByCreatedAtDesc(parent.getId());
      if (devices.size() == 1) {
        familyMatches.add(devices.get(0));
      }
    }
    if (familyMatches.size() == 1) {
      Device device = familyMatches.get(0);
      defaultChildLoginPasswordService.applyFamilyPasswordToDevice(device);
      auditService.log(
          AuditActionConstants.ONBOARDING_AUTH_CHILD_PASSWORD,
          "device:" + device.getId(),
          null,
          Map.of("source", "missing_android_id_family"));
      return OnboardingAuthResponse.builder().authenticated(true).build();
    }
    if (familyMatches.size() > 1) {
      throw new BusinessException(
          ErrorCode.FORBIDDEN,
          MessageConstants.MULTIPLE_DEVICES_MATCH_THIS_PASSWORD_RE_OPEN_THE_APP_AND_TRY_AGAIN);
    }

    return OnboardingAuthResponse.builder().authenticated(false).build();
  }

  private OnboardingAuthResponse authenticateAgainstDevice(Device device, String password) {
    if (device.getChildLoginPasswordHash() != null
        && passwordEncoder.matches(password, device.getChildLoginPasswordHash())) {
      auditService.log(
          AuditActionConstants.ONBOARDING_AUTH_CHILD_PASSWORD,
          "device:" + device.getId(),
          null,
          Map.of());
      return OnboardingAuthResponse.builder().authenticated(true).build();
    }

    var parentOpt = parentProfileRepository.findById(device.getParentProfileId());
    if (parentOpt.isPresent()
        && defaultChildLoginPasswordService.matchesDefault(parentOpt.get(), password)) {
      device.setChildLoginPasswordHash(parentOpt.get().getDefaultChildLoginPasswordHash());
      deviceRepository.save(device);
      auditService.log(
          AuditActionConstants.ONBOARDING_AUTH_CHILD_PASSWORD,
          "device:" + device.getId(),
          null,
          Map.of("source", "parent_default"));
      return OnboardingAuthResponse.builder().authenticated(true).build();
    }

    // Paired devices must use the family password from the parent account.
    OnboardingAuthResponse pairingMatch =
        authenticateAgainstActivePairingCode(password, device.getParentProfileId());
    if (pairingMatch.isAuthenticated()) {
      defaultChildLoginPasswordService.applyFamilyPasswordToDevice(device);
      auditService.log(
          AuditActionConstants.ONBOARDING_AUTH_CHILD_PASSWORD,
          "device:" + device.getId(),
          null,
          Map.of("source", "family_password"));
      return OnboardingAuthResponse.builder().authenticated(true).build();
    }

    auditService.log(
        AuditActionConstants.ONBOARDING_AUTH_FAILURE, "device:" + device.getId(), null, Map.of());
    return OnboardingAuthResponse.builder().authenticated(false).build();
  }

  private OnboardingAuthResponse authenticateAgainstActivePairingCode(
      String password, UUID parentProfileId) {
    Instant now = Instant.now();
    List<PairingCode> matches = new ArrayList<>();
    for (PairingCode code : pairingCodeRepository.findActiveUnusedWithPassword(now)) {
      if (parentProfileId != null && !parentProfileId.equals(code.getParentProfileId())) {
        continue;
      }
      if (passwordEncoder.matches(password, code.getChildLoginPasswordHash())) {
        matches.add(code);
      }
    }

    if (matches.isEmpty()) {
      auditService.log(
          AuditActionConstants.ONBOARDING_AUTH_FAILURE, "pairingCode:unknown", null, Map.of());
      return OnboardingAuthResponse.builder().authenticated(false).build();
    }
    if (matches.size() > 1 && parentProfileId == null) {
      throw new BusinessException(
          ErrorCode.FORBIDDEN,
          MessageConstants
              .MULTIPLE_ACTIVE_SETUP_SESSIONS_MATCH_THIS_PASSWORD_USE_RECOVERY_WITH_YOUR_SETUP_);
    }

    PairingCode pairingCode =
        matches.stream().max(Comparator.comparing(PairingCode::getCreatedAt)).orElseThrow();
    auditService.log(
        AuditActionConstants.ONBOARDING_AUTH_CHILD_PASSWORD,
        "parentProfile:" + pairingCode.getParentProfileId(),
        null,
        Map.of("pairingCode", redact(pairingCode.getCode())));

    return OnboardingAuthResponse.builder()
        .authenticated(true)
        .pairingCode(pairingCode.getCode())
        .build();
  }

  private TamperUnlockCode ensureRecoveryCode(Device device) {
    Instant now = Instant.now();
    var existing =
        tamperUnlockCodeRepository
            .findFirstByDeviceIdAndUsedAtIsNullAndExpiresAtAfterOrderByCreatedAtDesc(
                device.getId(), now);
    if (existing.isPresent()) {
      return existing.get();
    }

    tamperUnlockCodeRepository.invalidateActiveForDevice(device.getId(), now);
    String code = generateNumericCode();
    TamperUnlockCode created =
        TamperUnlockCode.builder()
            .deviceId(device.getId())
            .parentProfileId(device.getParentProfileId())
            .code(code)
            .expiresAt(now.plus(RECOVERY_CODE_TTL_MINUTES, ChronoUnit.MINUTES))
            .build();
    return tamperUnlockCodeRepository.save(created);
  }

  private String generateNumericCode() {
    int value = 100_000 + secureRandom.nextInt(900_000);
    return Integer.toString(value);
  }

  private static String redact(String code) {
    if (code == null || code.length() < 4) {
      return "****";
    }
    return code.substring(0, 2) + "****" + code.substring(code.length() - 2);
  }
}
