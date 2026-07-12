package com.parentalcontrol.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Admin master password for tamper bypass during setup/development. Disable in production ({@code
 * tamper.master-pin.enabled=false}) and replace with a server-managed secret before release.
 */
@Data
@Component
@ConfigurationProperties(prefix = "tamper.master-pin")
public class TamperMasterPinProperties {

  /** When false, only parent PIN and verification codes authorize tamper bypass. */
  private boolean enabled = false;

  /** Installer/admin master password — set via TAMPER_MASTER_PIN env when enabled for local dev. */
  private String pin = "";
}
