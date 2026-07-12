package com.parentalcontrol.common.constant;

/** HTTP header names used by the API and device clients. */
public final class HeaderConstants {

  public static final String AUTHORIZATION = "Authorization";
  public static final String BEARER_PREFIX = "Bearer ";
  public static final String CONTENT_TYPE = "Content-Type";
  public static final String ORIGIN = "Origin";
  public static final String STRIPE_SIGNATURE = "Stripe-Signature";

  public static final String DEVICE_ID = "X-Device-Id";
  public static final String DEVICE_TIMESTAMP = "X-Device-Timestamp";
  public static final String DEVICE_NONCE = "X-Device-Nonce";
  public static final String DEVICE_SIGNATURE = "X-Device-Signature";

  public static final String CONTENT_TYPE_JSON = "application/json";

  private HeaderConstants() {}
}
