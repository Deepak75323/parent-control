package com.parentalcontrol.common.util;

/** Redacts personal data before it appears in application logs. */
public final class SensitiveDataRedactor {

  private static final String REDACTED = "[REDACTED]";

  private SensitiveDataRedactor() {}

  public static String email(String value) {
    if (value == null || value.isBlank()) {
      return REDACTED;
    }
    int at = value.indexOf('@');
    if (at <= 1) {
      return REDACTED;
    }
    return value.charAt(0) + "***" + value.substring(at);
  }

  public static String androidId(String value) {
    if (value == null || value.isBlank()) {
      return REDACTED;
    }
    if (value.length() <= 8) {
      return REDACTED;
    }
    return value.substring(0, 4) + "…" + value.substring(value.length() - 4);
  }

  public static String displayName(String value) {
    return value == null || value.isBlank() ? REDACTED : REDACTED;
  }

  public static String deviceName(String value) {
    return value == null || value.isBlank() ? REDACTED : REDACTED;
  }
}
