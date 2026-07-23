package com.parentalcontrol.common.security;

import com.parentalcontrol.config.ApiPayloadEncryptionProperties;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import javax.crypto.Cipher;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * Airtel-compatible AES-CBC payload crypto (same key/IV/algorithm as admin-ui Encryptor).
 *
 * <p>Not for at-rest secrets — use {@link SecretEncryptionService} (AES-GCM) for that.
 */
@Component
@RequiredArgsConstructor
public class AirtelAesPayloadCrypto {

  private static final String TRANSFORMATION = "AES/CBC/PKCS5Padding";

  private final ApiPayloadEncryptionProperties properties;

  public String encrypt(String plaintext) {
    if (plaintext == null) {
      return null;
    }
    try {
      Cipher cipher = Cipher.getInstance(TRANSFORMATION);
      cipher.init(Cipher.ENCRYPT_MODE, keySpec(), ivSpec());
      byte[] encrypted = cipher.doFinal(plaintext.getBytes(StandardCharsets.UTF_8));
      return Base64.getEncoder().encodeToString(encrypted);
    } catch (Exception e) {
      throw new IllegalStateException("API payload encryption failed", e);
    }
  }

  public String decrypt(String ciphertextBase64) {
    if (ciphertextBase64 == null || ciphertextBase64.isBlank()) {
      return ciphertextBase64;
    }
    try {
      Cipher cipher = Cipher.getInstance(TRANSFORMATION);
      cipher.init(Cipher.DECRYPT_MODE, keySpec(), ivSpec());
      byte[] decrypted =
          cipher.doFinal(Base64.getDecoder().decode(ciphertextBase64.trim()));
      String text = new String(decrypted, StandardCharsets.UTF_8);
      // Match Airtel admin-ui: trim PKCS padding residue to last JSON object close.
      int lastBrace = text.lastIndexOf('}');
      if (lastBrace >= 0) {
        return text.substring(0, lastBrace + 1);
      }
      return text;
    } catch (Exception e) {
      throw new IllegalStateException("API payload decryption failed", e);
    }
  }

  private SecretKeySpec keySpec() {
    byte[] keyBytes = properties.getKey().getBytes(StandardCharsets.UTF_8);
    return new SecretKeySpec(keyBytes, "AES");
  }

  private IvParameterSpec ivSpec() {
    return new IvParameterSpec(properties.getIv().getBytes(StandardCharsets.UTF_8));
  }
}
