package com.parentalcontrol.common.constant;

/** Shared validation limits used across DTOs and services. */
public final class ValidationConstants {

  public static final int EMAIL_MAX_LENGTH = 255;
  public static final int PASSWORD_MIN_LENGTH = 8;
  public static final int PASSWORD_MAX_LENGTH = 128;
  public static final int PIN_MIN_LENGTH = 4;
  public static final int PIN_MAX_LENGTH = 64;
  public static final int PAIRING_CODE_LENGTH = 8;
  public static final int RECOVERY_CODE_LENGTH = 6;
  public static final int DEVICE_NAME_MAX_LENGTH = 100;

  public static final String EMAIL_REGEX = RegexConstants.EMAIL;

  private ValidationConstants() {}
}
