package com.parentalcontrol.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Admin master password for tamper bypass during setup/development.
 * Disable in production ({@code tamper.master-pin.enabled=false}) and replace with a
 * server-managed secret before release.
 */
@Data
@Component
@ConfigurationProperties(prefix = "tamper.master-pin")
public class TamperMasterPinProperties {

    /** When false, only parent PIN and verification codes authorize tamper bypass. */
    private boolean enabled = true;

    /**
     * Installer/admin master password. Keep in sync with child-app devTamper.ts for local UX hints.
     * Remove or override via environment before production release.
     */
    private String pin = "Deepak@123";
}
