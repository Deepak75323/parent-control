package com.parentalcontrol.common.constant;

/** Application-wide static values that are not timeouts, headers, or messages. */
public final class AppConstants {

  public static final String ENCRYPTION_PREFIX = "enc:v1:";
  public static final int GCM_IV_LENGTH_BYTES = 12;
  public static final int GCM_TAG_LENGTH_BITS = 128;

  public static final String HMAC_NONCE_KEY_PREFIX = "hmac:nonce:";

  public static final String FCM_LEGACY_SEND_URL = "https://fcm.googleapis.com/fcm/send";

  public static final String PLAY_INTEGRITY_DECODE_URL =
      "https://playintegrity.googleapis.com/v1/%s:decodeIntegrityToken";

  /** Protection level labels returned to clients. */
  public static final String PROTECTION_FULL = "FULL";

  public static final String PROTECTION_DEVICE_OWNER = "DEVICE_OWNER";
  public static final String PROTECTION_STANDARD = "STANDARD";

  /** Stream type wire values. */
  public static final String STREAM_TYPE_SCREEN = "SCREEN";

  public static final String STREAM_TYPE_CAMERA = "CAMERA";
  public static final String STREAM_TYPE_AUDIO = "AUDIO";

  private AppConstants() {}
}
