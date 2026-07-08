package com.parentalcontrol.device.service;

import com.parentalcontrol.common.exception.BusinessException;
import com.parentalcontrol.common.exception.ErrorCode;
import com.parentalcontrol.common.security.JwtProperties;
import com.parentalcontrol.common.security.JwtTokenProvider;
import com.parentalcontrol.common.security.SecurityUtils;
import com.parentalcontrol.device.domain.Device;
import com.parentalcontrol.device.domain.DevicePermissionStatus;
import com.parentalcontrol.device.domain.DeviceStatus;
import com.parentalcontrol.device.domain.PairingCode;
import com.parentalcontrol.device.dto.CreatePairingCodeRequest;
import com.parentalcontrol.device.dto.DevicePairRequest;
import com.parentalcontrol.device.dto.DevicePairResponse;
import com.parentalcontrol.device.dto.DeviceResponse;
import com.parentalcontrol.device.dto.HeartbeatRequest;
import com.parentalcontrol.device.dto.PairingCodeResponse;
import com.parentalcontrol.device.dto.PairingCodeStatusResponse;
import com.parentalcontrol.device.domain.DeviceProvisioningEvent;
import com.parentalcontrol.device.repository.DevicePermissionStatusRepository;
import com.parentalcontrol.device.repository.DeviceProvisioningEventRepository;
import com.parentalcontrol.device.repository.DeviceRepository;
import com.parentalcontrol.device.repository.PairingCodeRepository;
import com.parentalcontrol.billing.service.FeatureGateService;
import com.parentalcontrol.common.security.SecretEncryptionService;
import com.parentalcontrol.device.security.DeviceHmacService;
import com.parentalcontrol.observability.DeviceHealthObservabilityService;
import com.parentalcontrol.monitoring.kafka.MonitoringKafkaPublisher;
import com.parentalcontrol.monitoring.service.AlertService;
import com.parentalcontrol.monitoring.service.RealtimeEventService;
import com.parentalcontrol.user.domain.ParentProfile;
import com.parentalcontrol.user.repository.ParentProfileRepository;
import com.parentalcontrol.user.service.DefaultChildLoginPasswordService;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class DeviceService {

    private static final Logger log = LoggerFactory.getLogger(DeviceService.class);
    private static final int PAIRING_CODE_TTL_SECONDS = 900; // 15 minutes
    private static final String CODE_CHARS = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";

    private final DeviceRepository deviceRepository;
    private final PairingCodeRepository pairingCodeRepository;
    private final DevicePermissionStatusRepository permissionStatusRepository;
    private final ParentProfileRepository parentProfileRepository;
    private final JwtTokenProvider jwtTokenProvider;
    private final JwtProperties jwtProperties;
    private final RealtimeEventService realtimeEventService;
    private final AlertService alertService;
    private final DeviceProvisioningEventRepository deviceProvisioningEventRepository;
    private final DeviceHmacService deviceHmacService;
    private final FeatureGateService featureGateService;
    private final SecretEncryptionService secretEncryptionService;
    private final MonitoringKafkaPublisher monitoringKafkaPublisher;
    private final DeviceTrustService deviceTrustService;
    private final DeviceHealthObservabilityService deviceHealthObservabilityService;
    private final DeviceCertificationService deviceCertificationService;
    private final PasswordEncoder passwordEncoder;
    private final DefaultChildLoginPasswordService defaultChildLoginPasswordService;
    private final SecureRandom secureRandom = new SecureRandom();

    @Transactional
    public PairingCodeResponse createPairingCode(CreatePairingCodeRequest request) {
        UUID userId = SecurityUtils.requireUserId();
        ParentProfile parent = parentProfileRepository.findByUser_Id(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "Parent profile not found"));

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
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, "Could not generate unique pairing code");
        }

        Instant expiresAt = Instant.now().plusSeconds(PAIRING_CODE_TTL_SECONDS);

        PairingCode pairingCode = PairingCode.builder()
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
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, "Could not create pairing code");
        }

        log.info("Created pairing code {} for parentProfileId={} expiresAt={}",
                redactPairingCode(code), parent.getId(), expiresAt);

        String qrPayload = String.format("pcp://pair?code=%s&api=%s", code, "https://api.parentalcontrol.local");

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
        ParentProfile parent = parentProfileRepository.findByUser_Id(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "Parent profile not found"));

        PairingCode pairingCode = pairingCodeRepository.findByCodeIgnoreCase(rawCode.trim())
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "Pairing code not found"));
        if (!pairingCode.getParentProfileId().equals(parent.getId())) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "Pairing code not found");
        }

        PairingCodeStatusResponse.PairingCodeStatusResponseBuilder builder = PairingCodeStatusResponse.builder()
                .code(pairingCode.getCode())
                .used(pairingCode.isUsed())
                .expired(pairingCode.isExpired());

        if (pairingCode.getDeviceId() != null) {
            builder.deviceId(pairingCode.getDeviceId());
            deviceRepository.findById(pairingCode.getDeviceId())
                    .ifPresent(device -> builder.deviceName(device.getDeviceName()));
        }

        return builder.build();
    }

    @Transactional
    public DevicePairResponse pairDevice(DevicePairRequest request) {
        log.info("Pair attempt androidId={} code={} deviceName={}",
                request.getAndroidId(),
                redactPairingCode(request.getPairingCode()),
                request.getDeviceName());

        if (!"true".equalsIgnoreCase(request.getConsentAccepted())) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "Consent must be accepted on child device");
        }

        var trustEvaluation = deviceTrustService.evaluatePairing(
                request.getIntegrityToken(), request.getTrustSignals());

        PairingCode pairingCode = pairingCodeRepository
                .findByCodeIgnoreCase(request.getPairingCode().trim())
                .orElseThrow(() -> {
                    log.warn("Pair failed — code not found: {}", redactPairingCode(request.getPairingCode()));
                    return new BusinessException(ErrorCode.PAIRING_CODE_INVALID);
                });

        if (pairingCode.isUsed()) {
            log.warn("Pair failed — code already used: {}", redactPairingCode(pairingCode.getCode()));
            throw new BusinessException(ErrorCode.PAIRING_CODE_INVALID, "Pairing code already used");
        }
        if (pairingCode.isExpired()) {
            log.warn("Pair failed — code expired: {}", redactPairingCode(pairingCode.getCode()));
            throw new BusinessException(ErrorCode.PAIRING_CODE_EXPIRED);
        }

        ParentProfile parentProfile = parentProfileRepository.findByIdWithUser(pairingCode.getParentProfileId())
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "Parent profile not found"));
        String parentDisplayName = resolveParentDisplayName(parentProfile);
        String familyPasswordHash = defaultChildLoginPasswordService.requireHashForPairingCode(parentProfile);

        var existingOpt = deviceRepository.findByAndroidId(request.getAndroidId());
        if (existingOpt.isEmpty()) {
            featureGateService.requireDeviceSlot(pairingCode.getParentProfileId());
        }

        String hmacKey = deviceHmacService.generateSecret();
        Device device;

        if (existingOpt.isPresent()) {
            Device existing = existingOpt.get();
            if (!existing.getParentProfileId().equals(pairingCode.getParentProfileId())) {
                // Valid fresh pairing code re-assigns this physical device to the new parent.
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
            log.info("Re-paired existing device id={} androidId={}", device.getId(), request.getAndroidId());
        } else {
            device = Device.builder()
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

            permissionStatusRepository.save(DevicePermissionStatus.builder()
                    .deviceId(device.getId())
                    .build());
            log.info("Paired new device id={} androidId={}", device.getId(), request.getAndroidId());
        }

        deviceTrustService.applyEvaluation(device, trustEvaluation, request.getTrustSignals());
        device = deviceRepository.save(device);

        Instant now = Instant.now();
        int claimed = pairingCodeRepository.claimCode(pairingCode.getId(), device.getId(), now, now);
        if (claimed != 1) {
            log.error("Pair failed — atomic code claim lost race for code={} deviceId={}",
                    redactPairingCode(pairingCode.getCode()), device.getId());
            throw new BusinessException(ErrorCode.PAIRING_CODE_INVALID, "Pairing code already used");
        }

        UUID parentUserId = parentProfileRepository.findUserIdByProfileId(pairingCode.getParentProfileId())
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND));

        String deviceToken = jwtTokenProvider.createDeviceToken(
                device.getId(), parentUserId, device.getTokenVersion());

        realtimeEventService.publishDevicePaired(device, parentDisplayName);

        log.info("Pair success deviceId={} parentUserId={} parentDisplayName={}",
                device.getId(), parentUserId, parentDisplayName);

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
        ParentProfile parent = parentProfileRepository.findByUser_Id(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND));

        return deviceRepository.findByParentProfileIdOrderByCreatedAtDesc(parent.getId())
                .stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public DeviceResponse getDevice(UUID deviceId) {
        Device device = getOwnedDevice(deviceId);
        return toResponse(device);
    }

    @Transactional
    public DeviceResponse heartbeat(HeartbeatRequest request) {
        UUID deviceId = SecurityUtils.requireDeviceId();
        Device device = deviceRepository.findById(deviceId)
                .orElseThrow(() -> new BusinessException(ErrorCode.DEVICE_NOT_FOUND));

        deviceTrustService.evaluateHeartbeat(device, request.getIntegrityToken(), request.getTrustSignals());

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
        } else if (request.getMediaProjectionGranted() != null && !request.getMediaProjectionGranted()) {
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

        deviceCertificationService.applyHeartbeatOemData(device, request);

        device = deviceRepository.save(device);
        deviceHealthObservabilityService.recordHeartbeat(device);
        if (device.isMediaProjectionGranted()) {
            deviceHealthObservabilityService.recordMediaProjectionState(true);
        }
        realtimeEventService.publishDeviceStatus(device);
        monitoringKafkaPublisher.heartbeat(device.getId());
        alertService.checkLowBattery(device, device.getBatteryLevel());
        return toResponse(device);
    }

    private Device getOwnedDevice(UUID deviceId) {
        UUID userId = SecurityUtils.requireUserId();
        ParentProfile parent = parentProfileRepository.findByUser_Id(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND));

        Device device = deviceRepository.findById(deviceId)
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
                .build();
    }

    private String resolveProtectionLevel(Device device) {
        if (device.isDeviceOwnerActive() && device.isMediaProjectionGranted()) {
            return "FULL";
        }
        if (device.isDeviceOwnerActive()) {
            return "DEVICE_OWNER";
        }
        return "STANDARD";
    }

    private String resolveConsentStateOnLoss(Device device) {
        String current = device.getMediaProjectionConsentState();
        if (current != null && !current.isBlank() && !"GRANTED_ACTIVE".equals(current)) {
            return current;
        }
        return "LOST_AFTER_PROCESS_DEATH";
    }

    private void logProvisioningEvent(UUID deviceId, String type, String details) {
        deviceProvisioningEventRepository.save(DeviceProvisioningEvent.builder()
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
     * P1-5: Pairing codes are short (8 chars) and grant device control on entry, so they
     * must never appear in plaintext in server logs (CWE-532). We log only the first and
     * last character with the middle redacted — enough to correlate with a specific attempt
     * during incident response without exposing the secret to log aggregators.
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
