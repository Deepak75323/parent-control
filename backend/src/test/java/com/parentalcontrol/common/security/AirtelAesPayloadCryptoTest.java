package com.parentalcontrol.common.security;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.parentalcontrol.config.ApiPayloadEncryptionProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class AirtelAesPayloadCryptoTest {

  private AirtelAesPayloadCrypto crypto;

  @BeforeEach
  void setUp() {
    ApiPayloadEncryptionProperties props = new ApiPayloadEncryptionProperties();
    // Test-only 16-byte key/IV — not production secrets.
    props.setKey("0123456789abcdef");
    props.setIv("fedcba9876543210");
    crypto = new AirtelAesPayloadCrypto(props);
  }

  @Test
  void roundTripEncryptDecrypt_preservesJsonObject() {
    String plaintext = "{\"email\":\"dev.parent@local.test\",\"password\":\"Secret123!\"}";
    String cipher = crypto.encrypt(plaintext);
    assertTrue(cipher.length() > 16);
    assertEquals(plaintext, crypto.decrypt(cipher));
  }
}
