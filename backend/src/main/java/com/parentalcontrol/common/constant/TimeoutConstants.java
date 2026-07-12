package com.parentalcontrol.common.constant;

/** Timeouts, TTLs, thresholds, and other numeric application defaults. */
public final class TimeoutConstants {

  /** Pairing code lifetime (seconds). */
  public static final int PAIRING_CODE_TTL_SECONDS = 900;

  /** Child login recovery code lifetime (minutes). */
  public static final int RECOVERY_CODE_TTL_MINUTES = 15;

  /** Tamper unlock window after successful verification (minutes). */
  public static final int TAMPER_UNLOCK_TTL_MINUTES = 10;

  /** Tamper / recovery verification code lifetime (minutes). */
  public static final int TAMPER_CODE_TTL_MINUTES = 15;

  /** Stripe webhook timestamp tolerance (seconds). */
  public static final long STRIPE_WEBHOOK_TOLERANCE_SECONDS = 300;

  /** TURN session-bound credential TTL (seconds). */
  public static final long TURN_SESSION_TTL_SECONDS = 15L * 60L;

  /** Low-battery alert threshold (percent). */
  public static final int LOW_BATTERY_PERCENT_THRESHOLD = 20;

  /** Maximum page size for audit / list queries. */
  public static final int MAX_PAGE_SIZE = 100;

  /** Signaling duplicate-payload rejection window (seconds). */
  public static final long SIGNALING_DUPLICATE_WINDOW_SECONDS = 120;

  /** Default HTTP client connect timeout used by mobile / native clients (ms). */
  public static final int HTTP_CONNECT_TIMEOUT_MS = 15_000;

  /** Default HTTP client read timeout (ms). */
  public static final int HTTP_READ_TIMEOUT_MS = 15_000;

  private TimeoutConstants() {}
}
