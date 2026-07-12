package com.parentalcontrol.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "app.password-reset")
public class PasswordResetProperties {

  private boolean enabled = true;

  /** How long a reset link remains valid. */
  private int tokenTtlMinutes = 15;

  /**
   * Base URL for the parent app reset screen, without query string. Example:
   * https://parent.yourdomain.com/reset-password
   */
  private String resetUrlBase = "";
}
