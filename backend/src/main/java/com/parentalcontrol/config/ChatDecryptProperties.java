package com.parentalcontrol.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Getter
@Setter
@ConfigurationProperties(prefix = "app.chat.decrypt")
public class ChatDecryptProperties {

  /**
   * Ignored. Chat decrypt codes are created in the portal only — never from {@code CHAT_DECRYPT_PIN}.
   * Kept so existing env files do not fail property binding.
   */
  private String pin = "";

  public boolean isConfigured() {
    return false;
  }

  public String configuredPin() {
    return pin == null ? "" : pin.trim();
  }
}
