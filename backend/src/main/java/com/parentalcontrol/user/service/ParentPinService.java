package com.parentalcontrol.user.service;

import com.parentalcontrol.config.DevMasterPinSupport;
import com.parentalcontrol.audit.service.AuditService;
import com.parentalcontrol.common.exception.BusinessException;
import com.parentalcontrol.common.exception.ErrorCode;
import com.parentalcontrol.common.security.SecurityUtils;
import com.parentalcontrol.user.domain.User;
import com.parentalcontrol.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ParentPinService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuditService auditService;
    private final DevMasterPinSupport devMasterPinSupport;

    @Transactional
    public void setPin(String pin) {
        if (pin == null || pin.length() < 4) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "PIN must be at least 4 characters");
        }
        User user = requireParentUser();
        user.setParentPinHash(passwordEncoder.encode(pin));
        userRepository.save(user);
        auditService.log("PARENT_PIN_SET", "user:" + user.getId(), null, Map.of());
    }

    @Transactional
    public boolean verifyPin(String pin) {
        User user = requireParentUser();
        if (devMasterPinSupport.matches(pin)) {
            user.setSilentStreamPinUnlocked(true);
            userRepository.save(user);
            auditService.log("DEV_MASTER_PIN_VERIFIED", "user:" + user.getId(), null, Map.of());
            return true;
        }
        if (user.getParentPinHash() == null) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "Parent PIN not set");
        }
        if (!passwordEncoder.matches(pin, user.getParentPinHash())) {
            return false;
        }
        user.setSilentStreamPinUnlocked(true);
        userRepository.save(user);
        auditService.log("PARENT_PIN_VERIFIED", "user:" + user.getId(), null, Map.of());
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
     * Step-up authentication gate for sensitive parent actions (remote tap/swipe/type,
     * kiosk toggle, monitoring revoke, silent capture). Reuses the same per-user PIN
     * unlock flag as silent stream so a single PIN entry unlocks the session.
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
                    "Parent PIN is not configured. Open the parent app → Settings → Set Parent PIN "
                            + "to enable " + purpose + ".");
        }
        if (optionalPin == null || optionalPin.isBlank()) {
            throw new BusinessException(
                    ErrorCode.FORBIDDEN,
                    "Parent PIN required for " + purpose);
        }
        if (!verifyPin(optionalPin)) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "Invalid parent PIN");
        }
    }

    private User requireParentUser() {
        UUID userId = SecurityUtils.requireUserId();
        return userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "User not found"));
    }
}
