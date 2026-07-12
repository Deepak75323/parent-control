package com.parentalcontrol.device.service;

import com.parentalcontrol.audit.service.AuditService;
import com.parentalcontrol.common.constant.AuditActionConstants;
import com.parentalcontrol.common.constant.MessageConstants;
import com.parentalcontrol.common.constant.SecurityConstants;
import com.parentalcontrol.common.exception.BusinessException;
import com.parentalcontrol.common.exception.ErrorCode;
import com.parentalcontrol.common.security.SecurityUtils;
import com.parentalcontrol.config.DevMasterPinSupport;
import com.parentalcontrol.device.dao.DeviceDao;
import com.parentalcontrol.device.dao.TamperUnlockCodeDao;
import com.parentalcontrol.device.domain.Device;
import com.parentalcontrol.device.domain.TamperUnlockCode;
import com.parentalcontrol.device.dto.TamperAttemptRequest;
import com.parentalcontrol.device.dto.TamperAttemptResponse;
import com.parentalcontrol.device.dto.TamperCodeResponse;
import com.parentalcontrol.device.dto.TamperVerifyRequest;
import com.parentalcontrol.device.dto.TamperVerifyResponse;
import com.parentalcontrol.monitoring.service.AlertService;
import com.parentalcontrol.user.dao.ParentProfileDao;
import com.parentalcontrol.user.domain.ParentProfile;
import com.parentalcontrol.user.domain.User;
import java.security.SecureRandom;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Slf4j
public class TamperService {

  private static final int CODE_TTL_MINUTES = SecurityConstants.TAMPER_CODE_TTL_MINUTES;
  private static final int UNLOCK_TTL_MINUTES = SecurityConstants.TAMPER_UNLOCK_TTL_MINUTES;

  private final DeviceDao deviceRepository;
  private final TamperUnlockCodeDao tamperUnlockCodeRepository;
  private final ParentProfileDao parentProfileRepository;
  private final AlertService alertService;
  private final PasswordEncoder passwordEncoder;
  private final DevMasterPinSupport devMasterPinSupport;
  private final AuditService auditService;
  private final SecureRandom secureRandom = new SecureRandom();

  private enum AuthMethod {
    PARENT_PIN,
    MASTER_PIN,
    VERIFICATION_CODE
  }

  @Transactional
  public TamperAttemptResponse reportDisableAttempt() {
    return reportDisableAttempt(null);
  }

  @Transactional
  public TamperAttemptResponse reportDisableAttempt(TamperAttemptRequest request) {
    Device device = requireAuthenticatedDevice();
    ParentProfile parent = requireParentProfile(device);
    User user = parent.getUser();
    boolean pinConfigured = user != null && user.getParentPinHash() != null;
    String target = normalizeTarget(request != null ? request.getTarget() : null);

    auditService.log(
        AuditActionConstants.TAMPER_DISABLE_ATTEMPT,
        "device:" + device.getId(),
        device.getId(),
        Map.of("target", target));

    TamperUnlockCode activeCode = ensureActiveCode(device);
    alertService.checkDeviceAdminDisableAttempt(
        device, activeCode.getCode(), activeCode.getExpiresAt());

    return TamperAttemptResponse.builder()
        .parentPinConfigured(pinConfigured)
        .message(
            "Enter the parent password, the admin master password, or tap Use Verification Code "
                + "and enter the code from the parent app Alerts.")
        .build();
  }

  @Transactional
  public void reportAdminDisabled() {
    Device device = requireAuthenticatedDevice();
    device.setDeviceAdminActive(false);
    deviceRepository.save(device);
    auditService.log(
        AuditActionConstants.TAMPER_PROTECTION_DISABLED,
        "device:" + device.getId(),
        device.getId(),
        Map.of("target", "device_admin"));
    alertService.checkDeviceAdminDisabled(device);
  }

  @Transactional
  public TamperVerifyResponse verify(TamperVerifyRequest request) {
    Device device = requireAuthenticatedDevice();
    ParentProfile parent = requireParentProfile(device);
    User user = parent.getUser();
    if (user == null) {
      throw new BusinessException(ErrorCode.NOT_FOUND, MessageConstants.PARENT_ACCOUNT_NOT_FOUND);
    }

    String target = normalizeTarget(request.getTarget());
    Optional<AuthMethod> authMethod = Optional.empty();

    if (request.getPin() != null && !request.getPin().isBlank()) {
      authMethod = verifyPin(user, request.getPin().trim());
    } else if (request.getCode() != null && !request.getCode().isBlank()) {
      authMethod =
          verifyCode(device.getId(), request.getCode().trim())
              ? Optional.of(AuthMethod.VERIFICATION_CODE)
              : Optional.empty();
    } else {
      throw new BusinessException(
          ErrorCode.VALIDATION_ERROR, MessageConstants.PIN_OR_VERIFICATION_CODE_IS_REQUIRED);
    }

    if (authMethod.isEmpty()) {
      String method =
          request.getCode() != null && !request.getCode().isBlank()
              ? AuthMethod.VERIFICATION_CODE.name()
              : AuthMethod.PARENT_PIN.name();
      auditService.log(
          AuditActionConstants.TAMPER_AUTH_FAILURE,
          "device:" + device.getId(),
          device.getId(),
          Map.of("target", target, "method", method));
      return TamperVerifyResponse.builder().verified(false).build();
    }

    auditService.log(
        AuditActionConstants.TAMPER_AUTH_SUCCESS,
        "device:" + device.getId(),
        device.getId(),
        Map.of("target", target, "method", authMethod.get().name()));

    Instant unlockExpiresAt = Instant.now().plus(UNLOCK_TTL_MINUTES, ChronoUnit.MINUTES);
    return TamperVerifyResponse.builder().verified(true).unlockExpiresAt(unlockExpiresAt).build();
  }

  @Transactional(readOnly = true)
  public TamperCodeResponse getActiveCodeForParent(UUID deviceId) {
    Device device = getOwnedDevice(deviceId);
    return tamperUnlockCodeRepository
        .findFirstByDeviceIdAndUsedAtIsNullAndExpiresAtAfterOrderByCreatedAtDesc(
            device.getId(), Instant.now())
        .map(
            code ->
                TamperCodeResponse.builder()
                    .active(true)
                    .code(code.getCode())
                    .expiresAt(code.getExpiresAt())
                    .build())
        .orElse(TamperCodeResponse.builder().active(false).build());
  }

  private TamperUnlockCode ensureActiveCode(Device device) {
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
            .expiresAt(now.plus(CODE_TTL_MINUTES, ChronoUnit.MINUTES))
            .build();
    return tamperUnlockCodeRepository.save(created);
  }

  private Optional<AuthMethod> verifyPin(User user, String pin) {
    if (devMasterPinSupport.matches(pin)) {
      log.warn("Admin master password accepted for tamper verify (parent user={})", user.getId());
      return Optional.of(AuthMethod.MASTER_PIN);
    }
    if (user.getParentPinHash() == null) {
      throw new BusinessException(
          ErrorCode.FORBIDDEN,
          MessageConstants
              .PARENT_PASSWORD_IS_NOT_CONFIGURED_USE_THE_VERIFICATION_CODE_FROM_THE_PARENT_APP);
    }
    if (passwordEncoder.matches(pin, user.getParentPinHash())) {
      return Optional.of(AuthMethod.PARENT_PIN);
    }
    return Optional.empty();
  }

  private boolean verifyCode(UUID deviceId, String rawCode) {
    TamperUnlockCode code =
        tamperUnlockCodeRepository
            .findFirstByDeviceIdAndUsedAtIsNullAndExpiresAtAfterOrderByCreatedAtDesc(
                deviceId, Instant.now())
            .orElseThrow(
                () ->
                    new BusinessException(
                        ErrorCode.FORBIDDEN,
                        MessageConstants
                            .NO_ACTIVE_VERIFICATION_CODE_TAP_GET_VERIFICATION_CODE_AGAIN));
    if (!code.getCode().equalsIgnoreCase(rawCode.trim())) {
      return false;
    }
    code.setUsedAt(Instant.now());
    tamperUnlockCodeRepository.save(code);
    return true;
  }

  private String normalizeTarget(String raw) {
    if (raw == null || raw.isBlank()) {
      return "unknown";
    }
    return switch (raw.trim().toLowerCase()) {
      case "accessibility" -> "accessibility";
      case "device_admin", "deviceadmin" -> "device_admin";
      default -> raw.trim().toLowerCase();
    };
  }

  private String generateNumericCode() {
    int value = 100_000 + secureRandom.nextInt(900_000);
    return Integer.toString(value);
  }

  private Device requireAuthenticatedDevice() {
    UUID deviceId = SecurityUtils.requireDeviceId();
    return deviceRepository
        .findById(deviceId)
        .orElseThrow(() -> new BusinessException(ErrorCode.DEVICE_NOT_FOUND));
  }

  private ParentProfile requireParentProfile(Device device) {
    return parentProfileRepository
        .findByIdWithUser(device.getParentProfileId())
        .orElseThrow(
            () ->
                new BusinessException(
                    ErrorCode.NOT_FOUND, MessageConstants.PARENT_PROFILE_NOT_FOUND));
  }

  private Device getOwnedDevice(UUID deviceId) {
    UUID userId = SecurityUtils.requireUserId();
    ParentProfile parent =
        parentProfileRepository
            .findByUser_Id(userId)
            .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND));
    Device device =
        deviceRepository
            .findById(deviceId)
            .orElseThrow(() -> new BusinessException(ErrorCode.DEVICE_NOT_FOUND));
    if (!device.getParentProfileId().equals(parent.getId())) {
      throw new BusinessException(ErrorCode.FORBIDDEN);
    }
    return device;
  }

  /** Shared target strings for device + accessibility tamper flows. */
  public static final class TamperGateTargets {
    public static final String ACCESSIBILITY = "accessibility";
    public static final String DEVICE_ADMIN = "device_admin";

    private TamperGateTargets() {}
  }
}
