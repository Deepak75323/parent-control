package com.parentalcontrol.common.constant;

/**
 * Security-related aliases — prefer {@link HeaderConstants} and {@link TimeoutConstants} for new
 * code. Kept for existing callers.
 */
public final class SecurityConstants {

  public static final String HEADER_AUTHORIZATION = HeaderConstants.AUTHORIZATION;
  public static final String HEADER_BEARER_PREFIX = HeaderConstants.BEARER_PREFIX;
  public static final String HEADER_DEVICE_ID = HeaderConstants.DEVICE_ID;
  public static final String HEADER_DEVICE_SIGNATURE = HeaderConstants.DEVICE_SIGNATURE;
  public static final String HEADER_DEVICE_TIMESTAMP = HeaderConstants.DEVICE_TIMESTAMP;
  public static final String HEADER_CONTENT_TYPE = HeaderConstants.CONTENT_TYPE;
  public static final String HEADER_ORIGIN = HeaderConstants.ORIGIN;

  public static final String CONTENT_TYPE_JSON = HeaderConstants.CONTENT_TYPE_JSON;

  public static final int TAMPER_UNLOCK_TTL_MINUTES = TimeoutConstants.TAMPER_UNLOCK_TTL_MINUTES;
  public static final int TAMPER_CODE_TTL_MINUTES = TimeoutConstants.TAMPER_CODE_TTL_MINUTES;

  private SecurityConstants() {}
}
