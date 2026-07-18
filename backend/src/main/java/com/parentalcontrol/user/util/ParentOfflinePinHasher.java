package com.parentalcontrol.user.util;

import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Base64;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;

/**
 * PBKDF2 verifier for child-side offline parent-password unlock. Separate from the server bcrypt
 * hash so devices can verify without embedding bcrypt or receiving the login hash format.
 */
public final class ParentOfflinePinHasher {

  public static final int ITERATIONS = 120_000;
  public static final int SALT_BYTES = 16;
  public static final int KEY_BITS = 256;

  private ParentOfflinePinHasher() {}

  public record Material(String saltBase64, String hashBase64, int iterations) {}

  public static Material create(String pin) {
    try {
      byte[] salt = new byte[SALT_BYTES];
      new SecureRandom().nextBytes(salt);
      byte[] hash = derive(pin, salt, ITERATIONS);
      return new Material(
          Base64.getEncoder().encodeToString(salt),
          Base64.getEncoder().encodeToString(hash),
          ITERATIONS);
    } catch (GeneralSecurityException e) {
      throw new IllegalStateException("PBKDF2 unavailable", e);
    }
  }

  public static boolean matches(String pin, String saltBase64, String hashBase64, int iterations) {
    if (pin == null || pin.isBlank() || saltBase64 == null || hashBase64 == null || iterations < 1) {
      return false;
    }
    try {
      byte[] salt = Base64.getDecoder().decode(saltBase64);
      byte[] expected = Base64.getDecoder().decode(hashBase64);
      byte[] actual = derive(pin, salt, iterations);
      return Arrays.equals(expected, actual);
    } catch (IllegalArgumentException | GeneralSecurityException e) {
      return false;
    }
  }

  private static byte[] derive(String pin, byte[] salt, int iterations)
      throws GeneralSecurityException {
    PBEKeySpec spec =
        new PBEKeySpec(pin.toCharArray(), salt, iterations, KEY_BITS);
    SecretKeyFactory factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256");
    try {
      return factory.generateSecret(spec).getEncoded();
    } finally {
      spec.clearPassword();
    }
  }
}
