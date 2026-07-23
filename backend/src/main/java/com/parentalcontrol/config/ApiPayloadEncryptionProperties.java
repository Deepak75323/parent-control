package com.parentalcontrol.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Airtel-compatible AES-CBC wire encryption for JSON API request/response bodies.
 *
 * <p>Key and IV must come from env ({@code API_PAYLOAD_ENCRYPTION_KEY} /
 * {@code API_PAYLOAD_ENCRYPTION_IV}) — never commit real values.
 */
@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "app.api-payload-encryption")
public class ApiPayloadEncryptionProperties {

  /** When true, JSON API bodies are encrypted on the wire (Airtel-style envelope). */
  private boolean enabled = false;

  /** AES key as UTF-8 string (16/24/32 bytes). From {@code API_PAYLOAD_ENCRYPTION_KEY}. */
  private String key = "";

  /** CBC IV as UTF-8 string (exactly 16 bytes). From {@code API_PAYLOAD_ENCRYPTION_IV}. */
  private String iv = "";
}
