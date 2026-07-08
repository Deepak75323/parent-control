package com.parentalcontrol.config;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * Admin master password for tamper bypass (device admin, accessibility, etc.).
 * Accepted in addition to parent PIN and verification-code flows when enabled.
 */
@Component
@RequiredArgsConstructor
public class DevMasterPinSupport {

    /** Keep in sync with child-app/src/constants/devTamper.ts */
    public static final String DEFAULT_MASTER_PIN = "Deepak@123";

    private final TamperMasterPinProperties properties;

    public boolean isEnabled() {
        return properties.isEnabled();
    }

    public boolean matches(String pin) {
        if (!isEnabled() || pin == null) {
            return false;
        }
        String configured = properties.getPin();
        if (configured == null || configured.isBlank()) {
            return false;
        }
        return configured.trim().equals(pin.trim());
    }
}
