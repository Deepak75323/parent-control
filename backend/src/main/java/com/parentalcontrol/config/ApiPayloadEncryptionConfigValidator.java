package com.parentalcontrol.config;

import jakarta.annotation.PostConstruct;
import java.nio.charset.StandardCharsets;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class ApiPayloadEncryptionConfigValidator {

  private final ApiPayloadEncryptionProperties properties;

  @PostConstruct
  void validate() {
    if (!properties.isEnabled()) {
      return;
    }
    String key = properties.getKey() == null ? "" : properties.getKey();
    String iv = properties.getIv() == null ? "" : properties.getIv();
    if (key.isBlank() || iv.isBlank()) {
      throw new IllegalStateException(
          "API payload encryption is enabled but API_PAYLOAD_ENCRYPTION_KEY / "
              + "API_PAYLOAD_ENCRYPTION_IV are not set");
    }
    int keyLen = key.getBytes(StandardCharsets.UTF_8).length;
    if (keyLen != 16 && keyLen != 24 && keyLen != 32) {
      throw new IllegalStateException(
          "API_PAYLOAD_ENCRYPTION_KEY must be 16, 24, or 32 UTF-8 bytes (AES-128/192/256)");
    }
    int ivLen = iv.getBytes(StandardCharsets.UTF_8).length;
    if (ivLen != 16) {
      throw new IllegalStateException("API_PAYLOAD_ENCRYPTION_IV must be exactly 16 UTF-8 bytes");
    }
  }
}
