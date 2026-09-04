package com.parentalcontrol.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.util.StringUtils;

@Getter
@Setter
@ConfigurationProperties(prefix = "app.chat.decrypt")
public class ChatDecryptProperties {

  /**
   * Exactly 4 digits. Set once at server creation via {@code CHAT_DECRYPT_PIN}. Empty disables
   * decrypt unlock.
   */
  private String pin = "";

  public boolean isConfigured() {
    return StringUtils.hasText(pin) && pin.trim().matches("^\\d{4}$");
  }

  public String configuredPin() {
    return pin == null ? "" : pin.trim();
  }
}
