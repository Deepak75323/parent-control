package com.parentalcontrol.common.security;

import com.parentalcontrol.common.constant.AppConstants;
import com.parentalcontrol.config.EncryptionProperties;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;
import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class SecretEncryptionService {

  private static final String PREFIX = AppConstants.ENCRYPTION_PREFIX;
  private static final int GCM_IV_LENGTH = AppConstants.GCM_IV_LENGTH_BYTES;
  private static final int GCM_TAG_LENGTH = AppConstants.GCM_TAG_LENGTH_BITS;

  private final EncryptionProperties encryptionProperties;
  private final SecureRandom secureRandom = new SecureRandom();

  public String encrypt(String plaintext) {
    if (plaintext == null || plaintext.isBlank()) {
      return plaintext;
    }
    if (!encryptionProperties.isEnabled()) {
      return plaintext;
    }
    try {
      byte[] iv = new byte[GCM_IV_LENGTH];
      secureRandom.nextBytes(iv);
      Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
      cipher.init(Cipher.ENCRYPT_MODE, aesKey(), new GCMParameterSpec(GCM_TAG_LENGTH, iv));
      byte[] encrypted = cipher.doFinal(plaintext.getBytes(StandardCharsets.UTF_8));
      ByteBuffer buffer = ByteBuffer.allocate(iv.length + encrypted.length);
      buffer.put(iv);
      buffer.put(encrypted);
      return PREFIX + Base64.getEncoder().encodeToString(buffer.array());
    } catch (Exception e) {
      throw new IllegalStateException("Encryption failed", e);
    }
  }

  public String decrypt(String stored) {
    if (stored == null || stored.isBlank()) {
      return stored;
    }
    if (!stored.startsWith(PREFIX)) {
      return stored;
    }
    if (!encryptionProperties.isEnabled()) {
      return stored.substring(PREFIX.length());
    }
    try {
      byte[] decoded = Base64.getDecoder().decode(stored.substring(PREFIX.length()));
      ByteBuffer buffer = ByteBuffer.wrap(decoded);
      byte[] iv = new byte[GCM_IV_LENGTH];
      buffer.get(iv);
      byte[] ciphertext = new byte[buffer.remaining()];
      buffer.get(ciphertext);
      Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
      cipher.init(Cipher.DECRYPT_MODE, aesKey(), new GCMParameterSpec(GCM_TAG_LENGTH, iv));
      return new String(cipher.doFinal(ciphertext), StandardCharsets.UTF_8);
    } catch (Exception e) {
      throw new IllegalStateException("Decryption failed", e);
    }
  }

  private SecretKeySpec aesKey() throws Exception {
    MessageDigest digest = MessageDigest.getInstance("SHA-256");
    byte[] key = digest.digest(encryptionProperties.getKey().getBytes(StandardCharsets.UTF_8));
    return new SecretKeySpec(key, "AES");
  }
}
