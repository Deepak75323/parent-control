package com.parentalcontrol.device.service;

import com.parentalcontrol.audit.service.AuditService;
import com.parentalcontrol.common.exception.BusinessException;
import com.parentalcontrol.common.exception.ErrorCode;
import com.parentalcontrol.config.DevMasterPinSupport;
import com.parentalcontrol.device.domain.Device;
import com.parentalcontrol.device.domain.PairingCode;
import com.parentalcontrol.device.domain.TamperUnlockCode;
import com.parentalcontrol.device.dto.OnboardingAuthRequest;
import com.parentalcontrol.device.dto.OnboardingAuthResponse;
import com.parentalcontrol.device.dto.OnboardingRecoveryRequest;
import com.parentalcontrol.device.dto.OnboardingRecoveryResponse;
import com.parentalcontrol.device.repository.DeviceRepository;
import com.parentalcontrol.device.repository.PairingCodeRepository;
import com.parentalcontrol.device.repository.TamperUnlockCodeRepository;
import com.parentalcontrol.monitoring.service.AlertService;
import com.parentalcontrol.user.repository.ParentProfileRepository;
import com.parentalcontrol.user.service.DefaultChildLoginPasswordService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class OnboardingAuthService {

  private static final int RECOVERY_CODE_TTL_MINUTES = 15;

  private final PairingCodeRepository pairingCodeRepository;
  private final DeviceRepository deviceRepository;
  private final TamperUnlockCodeRepository tamperUnlockCodeRepository;
  private final ParentProfileRepository parentProfileRepository;
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

  @Transactional
  public OnboardingRecoveryResponse requestRecoveryCode(OnboardingRecoveryRequest request) {
    String androidId = request.getAndroidId().trim();
    Device device = deviceRepository.findByAndroidId(androidId)
        .orElseThrow(() -> new BusinessException(
            ErrorCode.NOT_FOUND,
            "This device is not paired yet. Ask your parent for the setup code from Pair & manage."));

    TamperUnlockCode activeCode = ensureRecoveryCode(device);
    alertService.checkChildLoginRecovery(device, activeCode.getCode(), activeCode.getExpiresAt());

    auditService.log(
        "ONBOARDING_RECOVERY_REQUEST",
        "device:" + device.getId(),
        device.getId(),
        Map.of());

    return OnboardingRecoveryResponse.builder()
        .codeSent(true)
        .message("Recovery code sent to the parent app. Open Alerts to view the code.")
        .build();
  }

  private OnboardingAuthResponse authenticateRecoveryCode(OnboardingAuthRequest request) {
    String androidId = request.getAndroidId();
    if (androidId == null || androidId.isBlank()) {
      throw new BusinessException(ErrorCode.VALIDATION_ERROR, "Device id is required for recovery");
    }

    Device device = deviceRepository.findByAndroidId(androidId.trim())
        .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "Device not found"));

    String rawCode = request.getRecoveryCode().trim();
    TamperUnlockCode code = tamperUnlockCodeRepository
        .findFirstByDeviceIdAndUsedAtIsNullAndExpiresAtAfterOrderByCreatedAtDesc(
            device.getId(), Instant.now())
        .orElseThrow(() -> new BusinessException(
            ErrorCode.FORBIDDEN, "No active recovery code. Tap Forgot password again to request a new code."));

    if (!code.getCode().equalsIgnoreCase(rawCode)) {
      auditService.log(
          "ONBOARDING_AUTH_FAILURE",
          "device:" + device.getId(),
          null,
          Map.of("method", "recovery_code"));
      return OnboardingAuthResponse.builder().authenticated(false).build();
    }

    code.setUsedAt(Instant.now());
    tamperUnlockCodeRepository.save(code);

    auditService.log(
        "ONBOARDING_AUTH_RECOVERY_CODE",
        "device:" + device.getId(),
        device.getId(),
        Map.of());

    return OnboardingAuthResponse.builder().authenticated(true).build();
  }

  private OnboardingAuthResponse authenticateSetupCode(OnboardingAuthRequest request) {
    if (request.getPairingCode() == null || request.getPairingCode().isBlank()) {
      throw new BusinessException(ErrorCode.VALIDATION_ERROR, "Setup code is required");
    }

    PairingCode pairingCode = pairingCodeRepository
        .findByCodeIgnoreCase(request.getPairingCode().trim())
        .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "Invalid setup code"));

    if (pairingCode.isUsed()) {
      throw new BusinessException(ErrorCode.FORBIDDEN, "Setup code already used");
    }
    if (pairingCode.isExpired()) {
      throw new BusinessException(ErrorCode.FORBIDDEN, "Setup code expired");
    }

    auditService.log(
        "ONBOARDING_AUTH_SETUP_CODE",
        "parentProfile:" + pairingCode.getParentProfileId(),
        null,
        Map.of("pairingCode", redact(pairingCode.getCode())));

    return OnboardingAuthResponse.builder()
        .authenticated(true)
        .pairingCode(pairingCode.getCode())
        .build();
  }

  private OnboardingAuthResponse authenticatePassword(OnboardingAuthRequest request) {
    String password = request.getChildLoginPassword();
    if (password == null || password.isBlank()) {
      throw new BusinessException(ErrorCode.VALIDATION_ERROR, "Child login password is required");
    }
    String trimmed = password.trim();

    if (devMasterPinSupport.matches(trimmed)) {
      auditService.log("ONBOARDING_AUTH_MASTER_PIN", "system", null, Map.of());
      return OnboardingAuthResponse.builder().authenticated(true).build();
    }

    String androidId = request.getAndroidId() != null ? request.getAndroidId().trim() : null;
    if (androidId != null && !androidId.isBlank()) {
      var deviceOpt = deviceRepository.findByAndroidId(androidId);
      if (deviceOpt.isPresent()) {
        return authenticateAgainstDevice(deviceOpt.get(), trimmed);
      }
    }

    return authenticateAgainstActivePairingCode(trimmed, null);
  }

  private OnboardingAuthResponse authenticateAgainstDevice(Device device, String password) {
    if (device.getChildLoginPasswordHash() != null
        && passwordEncoder.matches(password, device.getChildLoginPasswordHash())) {
      auditService.log(
          "ONBOARDING_AUTH_CHILD_PASSWORD",
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
          "ONBOARDING_AUTH_CHILD_PASSWORD",
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
          "ONBOARDING_AUTH_CHILD_PASSWORD",
          "device:" + device.getId(),
          null,
          Map.of("source", "family_password"));
      return OnboardingAuthResponse.builder().authenticated(true).build();
    }

    auditService.log(
        "ONBOARDING_AUTH_FAILURE",
        "device:" + device.getId(),
        null,
        Map.of());
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
      auditService.log("ONBOARDING_AUTH_FAILURE", "pairingCode:unknown", null, Map.of());
      return OnboardingAuthResponse.builder().authenticated(false).build();
    }
    if (matches.size() > 1 && parentProfileId == null) {
      throw new BusinessException(
          ErrorCode.FORBIDDEN,
          "Multiple active setup sessions match this password. Use recovery with your setup code.");
    }

    PairingCode pairingCode = matches.stream()
        .max(Comparator.comparing(PairingCode::getCreatedAt))
        .orElseThrow();
    auditService.log(
        "ONBOARDING_AUTH_CHILD_PASSWORD",
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
    var existing = tamperUnlockCodeRepository
        .findFirstByDeviceIdAndUsedAtIsNullAndExpiresAtAfterOrderByCreatedAtDesc(
            device.getId(), now);
    if (existing.isPresent()) {
      return existing.get();
    }

    tamperUnlockCodeRepository.invalidateActiveForDevice(device.getId(), now);
    String code = generateNumericCode();
    TamperUnlockCode created = TamperUnlockCode.builder()
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
