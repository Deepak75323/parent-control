package com.parentalcontrol.user.util;

import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

/** Normalize and validate public family handles (@optional prefix stripped, stored lowercase). */
public final class FamilyHandleNormalizer {

  private static final Pattern HANDLE_PATTERN =
      Pattern.compile("^[a-z0-9]([a-z0-9._-]{1,18}[a-z0-9])?$");

  private static final Set<String> RESERVED =
      Set.of(
          "admin",
          "support",
          "system",
          "parent",
          "child",
          "api",
          "null",
          "undefined",
          "help",
          "root",
          "official");

  private FamilyHandleNormalizer() {}

  /** Strip leading @, trim, lowercase. Returns null if blank. */
  public static String normalize(String raw) {
    if (raw == null) {
      return null;
    }
    String s = raw.trim();
    if (s.startsWith("@")) {
      s = s.substring(1).trim();
    }
    if (s.isEmpty()) {
      return null;
    }
    return s.toLowerCase(Locale.ROOT);
  }

  public static boolean isReserved(String normalized) {
    return normalized != null && RESERVED.contains(normalized);
  }

  public static boolean isValidFormat(String normalized) {
    if (normalized == null) {
      return false;
    }
    int len = normalized.length();
    if (len < 3 || len > 20) {
      return false;
    }
    return HANDLE_PATTERN.matcher(normalized).matches();
  }
}
