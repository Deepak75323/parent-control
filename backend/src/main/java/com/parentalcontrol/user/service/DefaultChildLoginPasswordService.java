package com.parentalcontrol.user.service;

import com.parentalcontrol.audit.service.AuditService;
import com.parentalcontrol.common.exception.BusinessException;
import com.parentalcontrol.common.exception.ErrorCode;
import com.parentalcontrol.common.security.SecurityUtils;
import com.parentalcontrol.device.domain.Device;
import com.parentalcontrol.device.repository.DeviceRepository;
import com.parentalcontrol.user.domain.ParentProfile;
import com.parentalcontrol.user.dto.DefaultChildLoginPasswordStatusResponse;
import com.parentalcontrol.user.repository.ParentProfileRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class DefaultChildLoginPasswordService {

    private final ParentProfileRepository parentProfileRepository;
    private final DeviceRepository deviceRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuditService auditService;

    @Transactional(readOnly = true)
    public DefaultChildLoginPasswordStatusResponse getStatus() {
        ParentProfile parent = requireParentProfile();
        int deviceCount = deviceRepository.findByParentProfileIdOrderByCreatedAtDesc(parent.getId()).size();
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
                "DEFAULT_CHILD_LOGIN_PASSWORD_SET",
                "parentProfile:" + parent.getId(),
                null,
                Map.of("linkedDevices", deviceRepository.findByParentProfileIdOrderByCreatedAtDesc(parent.getId()).size()));
        log.info("Default child login password updated for parentProfileId={}", parent.getId());
    }

    /** Hash copied onto every pairing code — one family password only. */
    @Transactional(readOnly = true)
    public String requireHashForPairingCode(ParentProfile parent) {
        if (!isConfigured(parent)) {
            throw new BusinessException(
                    ErrorCode.VALIDATION_ERROR,
                    "Set your family child login password in Settings before generating a pairing code");
        }
        return parent.getDefaultChildLoginPasswordHash();
    }

    /** Ensures a paired device stores the same hash as the parent account. */
    @Transactional
    public void applyFamilyPasswordToDevice(Device device) {
        parentProfileRepository.findById(device.getParentProfileId()).ifPresent(parent -> {
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
        deviceRepository.findByParentProfileIdOrderByCreatedAtDesc(parentProfileId).forEach(device -> {
            device.setChildLoginPasswordHash(hash);
            deviceRepository.save(device);
        });
    }

    private void validatePlaintext(String password) {
        if (password == null || password.isBlank()) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "Child login password is required");
        }
        if (password.trim().length() < 4) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "Child login password must be at least 4 characters");
        }
    }

    private ParentProfile requireParentProfile() {
        UUID userId = SecurityUtils.requireUserId();
        return parentProfileRepository.findByUser_Id(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "Parent profile not found"));
    }
}
