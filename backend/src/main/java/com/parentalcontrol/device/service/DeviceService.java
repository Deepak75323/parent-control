package com.parentalcontrol.device.service;

import com.parentalcontrol.billing.service.FeatureGateService;
import com.parentalcontrol.common.constant.AppConstants;
import com.parentalcontrol.common.constant.MessageConstants;
import com.parentalcontrol.common.constant.RegexConstants;
import com.parentalcontrol.common.constant.TimeoutConstants;
import com.parentalcontrol.common.exception.BusinessException;
import com.parentalcontrol.common.exception.ErrorCode;
import com.parentalcontrol.common.security.JwtProperties;
import com.parentalcontrol.common.security.JwtTokenProvider;
import com.parentalcontrol.common.security.SecretEncryptionService;
import com.parentalcontrol.common.security.SecurityUtils;
import com.parentalcontrol.common.util.SensitiveDataRedactor;
import com.parentalcontrol.device.dao.DeviceDao;
import com.parentalcontrol.device.dao.DevicePermissionStatusDao;
import com.parentalcontrol.device.dao.DeviceProvisioningEventDao;
import com.parentalcontrol.device.dao.PairingCodeDao;
import com.parentalcontrol.device.domain.Device;
import com.parentalcontrol.device.domain.DevicePermissionStatus;
import com.parentalcontrol.device.domain.DeviceProvisioningEvent;
import com.parentalcontrol.device.domain.DeviceStatus;
import com.parentalcontrol.device.domain.MediaProjectionConsentState;
import com.parentalcontrol.device.domain.PairingCode;
import com.parentalcontrol.device.dto.CreatePairingCodeRequest;
import com.parentalcontrol.device.dto.DevicePairRequest;
import com.parentalcontrol.device.dto.DevicePairResponse;
import com.parentalcontrol.device.dto.DeviceResponse;
import com.parentalcontrol.device.dto.DeviceWakeResponse;
import com.parentalcontrol.device.dto.HeartbeatRequest;
import com.parentalcontrol.device.dto.PairingCodeResponse;
import com.parentalcontrol.device.dto.PairingCodeStatusResponse;
import com.parentalcontrol.device.security.DeviceHmacService;
import com.parentalcontrol.monitoring.kafka.MonitoringKafkaPublisher;
import com.parentalcontrol.monitoring.service.AlertService;
import com.parentalcontrol.monitoring.service.RealtimeEventService;
import com.parentalcontrol.observability.DeviceHealthObservabilityService;
import com.parentalcontrol.user.dao.ParentProfileDao;
import com.parentalcontrol.user.domain.ParentProfile;
import com.parentalcontrol.user.service.DefaultChildLoginPasswordService;
import com.parentalcontrol.user.service.FamilyHandleService;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class DeviceService {

  private static final Logger log = LoggerFactory.getLogger(DeviceService.class);
  private static final int PAIRING_CODE_TTL_SECONDS = TimeoutConstants.PAIRING_CODE_TTL_SECONDS;
  private static final String CODE_CHARS = RegexConstants.PAIRING_CODE_ALPHABET;

  private final DeviceDao deviceRepository;
  private final PairingCodeDao pairingCodeRepository;
  private final DevicePermissionStatusDao permissionStatusRepository;
  private final ParentProfileDao parentProfileRepository;
  private final JwtTokenProvider jwtTokenProvider;
  private final JwtProperties jwtProperties;
  private final RealtimeEventService realtimeEventService;
  private final AlertService alertService;
  private final DeviceProvisioningEventDao deviceProvisioningEventRepository;
  private final DeviceHmacService deviceHmacService;
  private final FeatureGateService featureGateService;
  private final SecretEncryptionService secretEncryptionService;
  private final MonitoringKafkaPublisher monitoringKafkaPublisher;
  private final DeviceTrustService deviceTrustService;
  private final DeviceHealthObservabilityService deviceHealthObservabilityService;
  private final DeviceCertificationService deviceCertificationService;
  private final PasswordEncoder passwordEncoder;
  private final DefaultChildLoginPasswordService defaultChildLoginPasswordService;
  private final FamilyHandleService familyHandleService;
  private final FcmWakeService fcmWakeService;
  private final SecureRandom secureRandom = new SecureRandom();

  @Transactional
  public PairingCodeResponse createPairingCode(CreatePairingCodeRequest request) {
    UUID userId = SecurityUtils.requireUserId();
    ParentProfile parent =
        parentProfileRepository
            .findByUser_Id(userId)
            .orElseThrow(
                () ->
                    new BusinessException(
                        ErrorCode.NOT_FOUND, MessageConstants.PARENT_PROFILE_NOT_FOUND));

    familyHandleService.requireConfiguredForPairing(parent);
    String passwordHash = defaultChildLoginPasswordService.requireHashForPairingCode(parent);

    String code = null;
    for (int attempt = 0; attempt < 5; attempt++) {
      code = generatePairingCode();
      if (!pairingCodeRepository.existsByCodeIgnoreCase(code)) {
        break;
      }
      log.warn("Pairing code collision on attempt {} — regenerating", attempt + 1);
      code = null;
    }
    if (code == null) {
      throw new BusinessException(
          ErrorCode.INTERNAL_ERROR, MessageConstants.COULD_NOT_GENERATE_UNIQUE_PAIRING_CODE);
    }

    Instant now = Instant.now();
    pairingCodeRepository.expireActiveUnusedForParent(parent.getId(), now);
    Instant expiresAt = now.plusSeconds(PAIRING_CODE_TTL_SECONDS);

    PairingCode pairingCode =
        PairingCode.builder()
            .code(code)
            .parentProfileId(parent.getId())
            .childProfileId(request != null ? request.getChildProfileId() : null)
            .expiresAt(expiresAt)
            .childLoginPasswordHash(passwordHash)
            .build();
    try {
      pairingCodeRepository.save(pairingCode);
    } catch (DataIntegrityViolationException ex) {
      log.error("Failed to persist pairing code after collision checks", ex);
      throw new BusinessException(
          ErrorCode.INTERNAL_ERROR, MessageConstants.COULD_NOT_CREATE_PAIRING_CODE);
    }

    log.info(
        "Created pairing code {} for parentProfileId={} expiresAt={}",
        redactPairingCode(code),
        parent.getId(),
        expiresAt);

    String qrPayload =
        String.format("pcp://pair?code=%s&api=%s", code, "https://api.parentalcontrol.local");

    return PairingCodeResponse.builder()
        .code(code)
        .qrPayload(qrPayload)
        .expiresAt(expiresAt)
        .expiresInSeconds(PAIRING_CODE_TTL_SECONDS)
        .build();
  }

  @Transactional(readOnly = true)
  public PairingCodeStatusResponse getPairingCodeStatus(String rawCode) {
    UUID userId = SecurityUtils.requireUserId();
    ParentProfile parent =
        parentProfileRepository
            .findByUser_Id(userId)
            .orElseThrow(
                () ->
                    new BusinessException(
                        ErrorCode.NOT_FOUND, MessageConstants.PARENT_PROFILE_NOT_FOUND));

    PairingCode pairingCode =
        pairingCodeRepository
            .findByCodeIgnoreCase(rawCode.trim())
            .orElseThrow(
                () ->
                    new BusinessException(
                        ErrorCode.NOT_FOUND, MessageConstants.PAIRING_CODE_NOT_FOUND));
    if (!pairingCode.getParentProfileId().equals(parent.getId())) {
      throw new BusinessException(ErrorCode.FORBIDDEN, MessageConstants.PAIRING_CODE_NOT_FOUND);
    }

    PairingCodeStatusResponse.PairingCodeStatusResponseBuilder builder =
        PairingCodeStatusResponse.builder()
            .code(pairingCode.getCode())
            .used(pairingCode.isUsed())
            .expired(pairingCode.isExpired());

    if (pairingCode.getDeviceId() != null) {
      builder.deviceId(pairingCode.getDeviceId());
      deviceRepository
          .findById(pairingCode.getDeviceId())
          .ifPresent(device -> builder.deviceName(device.getDeviceName()));
    }

    return builder.build();
  }

  @Transactional
  public DevicePairResponse pairDevice(DevicePairRequest request) {
    log.info(
        "Pair attempt androidId={} code={} deviceName={}",
        SensitiveDataRedactor.androidId(request.getAndroidId()),
        redactPairingCode(request.getPairingCode()),
        SensitiveDataRedactor.deviceName(request.getDeviceName()));

    if (!"true".equalsIgnoreCase(request.getConsentAccepted())) {
      throw new BusinessException(
          ErrorCode.VALIDATION_ERROR, MessageConstants.CONSENT_MUST_BE_ACCEPTED_ON_CHILD_DEVICE);
    }

    var trustEvaluation =
        deviceTrustService.evaluatePairing(request.getIntegrityToken(), request.getTrustSignals());

    PairingCode pairingCode =
        pairingCodeRepository
            .findByCodeIgnoreCase(request.getPairingCode().trim())
            .orElseThrow(
                () -> {
                  log.warn(
                      "Pair failed — code not found: {}",
                      redactPairingCode(request.getPairingCode()));
                  return new BusinessException(ErrorCode.PAIRING_CODE_INVALID);
                });

    if (pairingCode.isUsed()) {
      log.warn("Pair failed — code already used: {}", redactPairingCode(pairingCode.getCode()));
      throw new BusinessException(
          ErrorCode.PAIRING_CODE_INVALID, MessageConstants.PAIRING_CODE_ALREADY_USED);
    }
    if (pairingCode.isExpired()) {
      log.warn("Pair failed — code expired: {}", redactPairingCode(pairingCode.getCode()));
      throw new BusinessException(ErrorCode.PAIRING_CODE_EXPIRED);
    }

    ParentProfile parentProfile =
        parentProfileRepository
            .findByIdWithUser(pairingCode.getParentProfileId())
            .orElseThrow(
                () ->
                    new BusinessException(
                        ErrorCode.NOT_FOUND, MessageConstants.PARENT_PROFILE_NOT_FOUND));
    String parentDisplayName = resolveParentDisplayName(parentProfile);
    String familyPasswordHash =
        defaultChildLoginPasswordService.requireHashForPairingCode(parentProfile);

    var existingOpt = deviceRepository.findByAndroidId(request.getAndroidId());
    if (existingOpt.isEmpty()) {
      featureGateService.requireDeviceSlot(pairingCode.getParentProfileId());
    }

    String hmacKey = deviceHmacService.generateSecret();
    Device device;
    UUID previousParentProfileId = null;

    if (existingOpt.isPresent()) {
      Device existing = existingOpt.get();
      if (!existing.getParentProfileId().equals(pairingCode.getParentProfileId())) {
        // Valid fresh pairing code re-assigns this physical device to the new parent.
        previousParentProfileId = existing.getParentProfileId();
        featureGateService.requireDeviceSlot(pairingCode.getParentProfileId());
        existing.setParentProfileId(pairingCode.getParentProfileId());
      }
      if (pairingCode.getChildProfileId() != null) {
        existing.setChildProfileId(pairingCode.getChildProfileId());
      }
      existing.setDeviceName(request.getDeviceName());
      existing.setDeviceModel(request.getDeviceModel());
      existing.setOsVersion(request.getOsVersion());
      existing.setAppVersion(request.getAppVersion());
      existing.setFcmToken(request.getFcmToken());
      existing.setDeviceHmacKey(secretEncryptionService.encrypt(hmacKey));
      existing.setStatus(DeviceStatus.ONLINE);
      existing.setLastSeenAt(Instant.now());
      existing.setTokenVersion(existing.getTokenVersion() + 1);
      existing.setChildLoginPasswordHash(familyPasswordHash);
      device = deviceRepository.save(existing);
      log.info(
          "Re-paired existing device id={} androidId={} previousParentProfileId={}",
          device.getId(),
          SensitiveDataRedactor.androidId(request.getAndroidId()),
          previousParentProfileId);
    } else {
      device =
          Device.builder()
              .parentProfileId(pairingCode.getParentProfileId())
              .childProfileId(pairingCode.getChildProfileId())
              .deviceName(request.getDeviceName())
              .deviceModel(request.getDeviceModel())
              .osVersion(request.getOsVersion())
              .appVersion(request.getAppVersion())
              .androidId(request.getAndroidId())
              .fcmToken(request.getFcmToken())
              .deviceHmacKey(secretEncryptionService.encrypt(hmacKey))
              .childLoginPasswordHash(familyPasswordHash)
              .status(DeviceStatus.ONLINE)
              .lastSeenAt(Instant.now())
              .build();
      device = deviceRepository.save(device);

      permissionStatusRepository.save(
          DevicePermissionStatus.builder().deviceId(device.getId()).build());
      log.info(
          "Paired new device id={} androidId={}",
          device.getId(),
          SensitiveDataRedactor.androidId(request.getAndroidId()));
    }

    deviceTrustService.applyEvaluation(device, trustEvaluation, request.getTrustSignals());
    device = deviceRepository.save(device);

    Instant now = Instant.now();
    int claimed = pairingCodeRepository.claimCode(pairingCode.getId(), device.getId(), now, now);
    if (claimed != 1) {
      log.error(
          "Pair failed — atomic code claim lost race for code={} deviceId={}",
          redactPairingCode(pairingCode.getCode()),
          device.getId());
      throw new BusinessException(
          ErrorCode.PAIRING_CODE_INVALID, MessageConstants.PAIRING_CODE_ALREADY_USED);
    }

    UUID parentUserId =
        parentProfileRepository
            .findUserIdByProfileId(pairingCode.getParentProfileId())
            .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND));

    String deviceToken =
        jwtTokenProvider.createDeviceToken(device.getId(), parentUserId, device.getTokenVersion());

    if (previousParentProfileId != null) {
      realtimeEventService.publishDeviceRemovedFromParent(
          previousParentProfileId, device.getId(), device.getDeviceName());
    }
    realtimeEventService.publishDevicePaired(device, parentDisplayName);

    log.info(
        "Pair success deviceId={} parentUserId={} parentDisplayName={} crossParentReassign={}",
        device.getId(),
        parentUserId,
        SensitiveDataRedactor.displayName(parentDisplayName),
        previousParentProfileId != null);

    return DevicePairResponse.builder()
        .deviceId(device.getId())
        .deviceToken(deviceToken)
        .deviceHmacKey(hmacKey)
        .tokenType("Bearer")
        .expiresInSeconds(jwtProperties.getDeviceTokenExpirationMs() / 1000)
        .parentDisplayName(parentDisplayName)
        .deviceName(device.getDeviceName())
        .build();
  }

  @Transactional(readOnly = true)
  public List<DeviceResponse> listDevicesForParent() {
    UUID userId = SecurityUtils.requireUserId();
    ParentProfile parent =
        parentProfileRepository
            .findByUser_Id(userId)
            .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND));

    return deviceRepository.findActiveByParentProfileIdOrderByCreatedAtDesc(parent.getId()).stream()
        .map(this::toResponse)
        .toList();
  }

  @Transactional(readOnly = true)
  public DeviceResponse getDevice(UUID deviceId) {
    Device device = getOwnedDevice(deviceId);
    return toResponse(device);
  }

  /**
   * Parent-initiated FCM wake for a specific child device.
   *
   * <p>Fires a high-priority {@code HEARTBEAT_NUDGE} data message so the child app posts a fresh
   * heartbeat without waiting for its 5-minute alarm or the 2-minute nudge sweep. This is the
   * mechanism the parent UI uses to remove the "device offline" gate when the parent taps live view
   * moments after the child reconnects.
   *
   * <p>Returns the current status/{@code lastSeenAt} so the caller can poll for freshness. FCM
   * dispatch is best-effort — {@link FcmWakeService} logs (and swallows) transport errors so a
   * broken FCM configuration does not block the parent's retry loop.
   */
  @Transactional(readOnly = true)
  public DeviceWakeResponse wakeDevice(UUID deviceId) {
    Device device = getOwnedDevice(deviceId);
    String tokenPresent =
        device.getFcmToken() != null && !device.getFcmToken().isBlank() ? "yes" : "no";
    log.info(
        "PARENT_WAKE_REQUEST deviceId={} status={} lastSeenAt={} fcmToken={}",
        device.getId(),
        device.getStatus(),
        device.getLastSeenAt(),
        tokenPresent);
    fcmWakeService.wakeDevice(device, "HEARTBEAT_NUDGE", Map.of("source", "parent_wake"));
    return DeviceWakeResponse.builder()
        .deviceId(device.getId())
        .fcmDispatched(device.getFcmToken() != null && !device.getFcmToken().isBlank())
        .status(device.getStatus())
        .lastSeenAt(device.getLastSeenAt())
        .build();
  }

  @Transactional
  public DeviceResponse heartbeat(HeartbeatRequest request) {
    UUID deviceId = SecurityUtils.requireDeviceId();
    Device device =
        deviceRepository
            .findById(deviceId)
            .orElseThrow(() -> new BusinessException(ErrorCode.DEVICE_NOT_FOUND));

    deviceTrustService.evaluateHeartbeat(
        device, request.getIntegrityToken(), request.getTrustSignals());

    // Update device to ONLINE status since we received a successful heartbeat
    boolean wasOffline = device.getStatus() == DeviceStatus.OFFLINE;
    device.setStatus(DeviceStatus.ONLINE);
    device.setLastSeenAt(Instant.now());
    if (request.getBatteryLevel() != null) {
      device.setBatteryLevel(request.getBatteryLevel());
    }
    if (request.getCharging() != null) {
      device.setCharging(request.getCharging());
    }
    if (request.getNetworkType() != null) {
      device.setNetworkType(request.getNetworkType());
    }
    if (request.getLatitude() != null) {
      device.setLastLatitude(request.getLatitude());
    }
    if (request.getLongitude() != null) {
      device.setLastLongitude(request.getLongitude());
    }
    if (request.getForegroundApp() != null) {
      device.setForegroundApp(request.getForegroundApp());
    }
    if (request.getFcmToken() != null) {
      device.setFcmToken(request.getFcmToken());
    }
    if (request.getDeviceOwnerActive() != null) {
      boolean wasOwner = device.isDeviceOwnerActive();
      device.setDeviceOwnerActive(request.getDeviceOwnerActive());
      if (request.getDeviceOwnerActive() && !wasOwner) {
        device.setDeviceOwnerProvisionedAt(Instant.now());
        logProvisioningEvent(device.getId(), "DEVICE_OWNER_ACTIVE", null);
      }
    }
    if (request.getDeviceAdminActive() != null) {
      boolean wasAdmin = device.isDeviceAdminActive();
      boolean nowAdmin = request.getDeviceAdminActive();
      device.setDeviceAdminActive(nowAdmin);
      if (wasAdmin && !nowAdmin) {
        alertService.checkDeviceAdminDisabled(device);
      }
    }
    if (request.getMediaProjectionGranted() != null) {
      boolean wasGranted = device.isMediaProjectionGranted();
      boolean nowGranted = request.getMediaProjectionGranted();
      device.setMediaProjectionGranted(nowGranted);
      if (nowGranted) {
        logProvisioningEvent(device.getId(), "MEDIA_PROJECTION_GRANTED", null);
      } else if (wasGranted) {
        alertService.checkProjectionLost(device);
      }
    }
    if (request.getMediaProjectionConsentState() != null) {
      device.setMediaProjectionConsentState(request.getMediaProjectionConsentState());
      if (!device.isMediaProjectionGranted()) {
        device.setMediaProjectionGranted(false);
      }
    } else if (request.getMediaProjectionGranted() != null
        && !request.getMediaProjectionGranted()) {
      device.setMediaProjectionConsentState(resolveConsentStateOnLoss(device));
    }
    if (request.getAccessibilityEnabled() != null) {
      device.setAccessibilityEnabled(request.getAccessibilityEnabled());
    }
    if (request.getAccessibilityServiceConnected() != null) {
      boolean wasConnected = device.isAccessibilityServiceConnected();
      boolean nowConnected = request.getAccessibilityServiceConnected();
      device.setAccessibilityServiceConnected(nowConnected);
      if (wasConnected && !nowConnected && device.isAccessibilityEnabled()) {
        alertService.checkAccessibilityServiceDisconnected(device);
      }
    }
    if (request.getSilentScreenCapable() != null) {
      device.setSilentScreenCapable(request.getSilentScreenCapable());
    }
    if (request.getKioskModeActive() != null) {
      device.setKioskModeActive(request.getKioskModeActive());
    }
    if (request.getProtectionLevel() != null) {
      device.setProtectionLevel(request.getProtectionLevel());
    } else {
      device.setProtectionLevel(resolveProtectionLevel(device));
    }
    if (request.getSilentScreenCapable() == null) {
      device.setSilentScreenCapable(
          device.isDeviceOwnerActive() && device.isMediaProjectionGranted());
    }

    boolean wasMonitoringDegradedBefore = wasMonitoringDegraded(device);
    deviceCertificationService.applyHeartbeatOemData(device, request);

    if (Boolean.TRUE.equals(request.getMonitoringDegraded()) && !wasMonitoringDegradedBefore) {
      alertService.checkMonitoringDegraded(
          device,
          request.getMonitoringMode(),
          request.getMonitoringDegradedReason(),
          request.getStandbyBucket());
      deviceHealthObservabilityService.recordMonitoringDegraded(
          request.getMonitoringMode(), request.getMonitoringDegradedReason());
    } else if (Boolean.TRUE.equals(request.getMonitoringDegraded())) {
      deviceHealthObservabilityService.recordMonitoringDegraded(
          request.getMonitoringMode(), request.getMonitoringDegradedReason());
    }

    device = deviceRepository.save(device);
    deviceHealthObservabilityService.recordHeartbeat(device);
    if (device.isMediaProjectionGranted()) {
      deviceHealthObservabilityService.recordMediaProjectionState(true);
    }

    // Publish device status update - this will notify parent in real-time
    realtimeEventService.publishDeviceStatus(device);

    // If device was offline and is now online, log the recovery
    if (wasOffline) {
      log.info(
          "Device {} came back ONLINE — lastSeenAt={}", device.getId(), device.getLastSeenAt());
    }

    monitoringKafkaPublisher.heartbeat(device.getId());
    alertService.checkLowBattery(device, device.getBatteryLevel());
    if (request.getOfflineUnlockFailures() != null && request.getOfflineUnlockFailures() > 0) {
      alertService.checkOfflineUnlockFailures(device, request.getOfflineUnlockFailures());
    }
    return toResponse(device);
  }

  private boolean wasMonitoringDegraded(Device device) {
    Map<String, Object> diag = device.getOemDiagnosticsJson();
    if (diag == null) {
      return false;
    }
    Object raw = diag.get("monitoringDegraded");
    if (raw instanceof Boolean b) {
      return b;
    }
    return "true".equalsIgnoreCase(String.valueOf(raw));
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

  private DeviceResponse toResponse(Device device) {
    return DeviceResponse.builder()
        .id(device.getId())
        .deviceName(device.getDeviceName())
        .deviceModel(device.getDeviceModel())
        .status(device.getStatus())
        .batteryLevel(device.getBatteryLevel())
        .charging(device.getCharging())
        .networkType(device.getNetworkType())
        .lastSeenAt(device.getLastSeenAt())
        .lastLatitude(device.getLastLatitude())
        .lastLongitude(device.getLastLongitude())
        .foregroundApp(device.getForegroundApp())
        .childProfileId(device.getChildProfileId())
        .deviceOwnerActive(device.isDeviceOwnerActive())
        .deviceAdminActive(device.isDeviceAdminActive())
        .mediaProjectionGranted(device.isMediaProjectionGranted())
        .mediaProjectionConsentState(device.getMediaProjectionConsentState())
        .silentScreenCapable(device.isSilentScreenCapable())
        .protectionLevel(device.getProtectionLevel())
        .kioskModeActive(device.isKioskModeActive())
        .accessibilityEnabled(device.isAccessibilityEnabled())
        .accessibilityServiceConnected(device.isAccessibilityServiceConnected())
        .trustLevel(device.getTrustLevel())
        .trustScore(device.getTrustScore())
        .lastTrustEvaluatedAt(device.getLastTrustEvaluatedAt())
        .oemVendor(device.getOemVendor())
        .deviceReadinessScore(device.getDeviceReadinessScore())
        .android16ReadinessScore(device.getAndroid16ReadinessScore())
        .oemSetupComplete(device.isOemSetupComplete())
        .certificationStatus(device.getCertificationStatus())
        .certificationScore(device.getCertificationScore())
        .lastCertificationAt(device.getLastCertificationAt())
        .monitoringDegraded(readMonitoringFlag(device, "monitoringDegraded"))
        .monitoringMode(readMonitoringString(device, "monitoringMode"))
        .standbyBucket(readMonitoringString(device, "standbyBucket"))
        .build();
  }

  private Boolean readMonitoringFlag(Device device, String key) {
    Map<String, Object> diag = device.getOemDiagnosticsJson();
    if (diag == null) {
      return null;
    }
    Object raw = diag.get(key);
    if (raw instanceof Boolean b) {
      return b;
    }
    if (raw == null) {
      return null;
    }
    return "true".equalsIgnoreCase(String.valueOf(raw));
  }

  private String readMonitoringString(Device device, String key) {
    Map<String, Object> diag = device.getOemDiagnosticsJson();
    if (diag == null || diag.get(key) == null) {
      return null;
    }
    return String.valueOf(diag.get(key));
  }

  private String resolveProtectionLevel(Device device) {
    if (device.isDeviceOwnerActive() && device.isMediaProjectionGranted()) {
      return AppConstants.PROTECTION_FULL;
    }
    if (device.isDeviceOwnerActive()) {
      return AppConstants.PROTECTION_DEVICE_OWNER;
    }
    return AppConstants.PROTECTION_STANDARD;
  }

  private String resolveConsentStateOnLoss(Device device) {
    String current = device.getMediaProjectionConsentState();
    if (current != null
        && !current.isBlank()
        && !MediaProjectionConsentState.GRANTED_ACTIVE.equalsWire(current)) {
      return current;
    }
    return MediaProjectionConsentState.LOST_AFTER_PROCESS_DEATH.name();
  }

  private void logProvisioningEvent(UUID deviceId, String type, String details) {
    deviceProvisioningEventRepository.save(
        DeviceProvisioningEvent.builder()
            .deviceId(deviceId)
            .eventType(type)
            .details(details != null ? details : "{}")
            .build());
  }

  private String generatePairingCode() {
    StringBuilder sb = new StringBuilder(8);
    for (int i = 0; i < 8; i++) {
      sb.append(CODE_CHARS.charAt(secureRandom.nextInt(CODE_CHARS.length())));
    }
    return sb.toString();
  }

  /**
   * P1-5: Pairing codes are short (8 chars) and grant device control on entry, so they must never
   * appear in plaintext in server logs (CWE-532). We log only the first and last character with the
   * middle redacted — enough to correlate with a specific attempt during incident response without
   * exposing the secret to log aggregators.
   */
  private static String redactPairingCode(String code) {
    if (code == null) return "null";
    String trimmed = code.trim();
    if (trimmed.isEmpty()) return "<empty>";
    if (trimmed.length() <= 2) return "**";
    return trimmed.charAt(0) + "******" + trimmed.charAt(trimmed.length() - 1);
  }

  private String resolveParentDisplayName(ParentProfile parent) {
    String name = (parent.getFirstName() + " " + parent.getLastName()).trim();
    if (!name.isBlank()) {
      return name;
    }
    if (parent.getUser() != null && parent.getUser().getEmail() != null) {
      String email = parent.getUser().getEmail().trim();
      if (!email.isBlank()) {
        return email;
      }
    }
    return "Parent";
  }
}
