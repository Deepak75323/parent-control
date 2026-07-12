package com.parentalcontrol.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "app.email-verification")
public class EmailVerificationProperties {

  /** When true, trial and paid features require a verified email. */
  private boolean enforcementEnabled = false;

  private int tokenTtlHours = 24;

  /**
   * Base URL for the parent app verify screen, without query string. Example:
   * https://parent.yourdomain.com/verify-email
   */
  private String verifyUrlBase = "";
}
