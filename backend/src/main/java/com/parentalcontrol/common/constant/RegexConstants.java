package com.parentalcontrol.common.constant;

/** Shared regex patterns. */
public final class RegexConstants {

  public static final String EMAIL = "^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+$";

  /** Pairing / setup code alphabet (excludes ambiguous characters). */
  public static final String PAIRING_CODE_ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";

  private RegexConstants() {}
}
