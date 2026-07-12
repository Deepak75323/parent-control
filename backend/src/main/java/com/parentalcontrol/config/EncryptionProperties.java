package com.parentalcontrol.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "app.encryption")
public class EncryptionProperties {

  /** Encrypt device HMAC keys and other secrets at rest. */
  private boolean enabled = false;

  /** Min 32 chars in production — use APP_ENCRYPTION_KEY env. */
  private String key = "";
}
