package com.parentalcontrol.common.constant;

/** Redis / cache key prefixes. */
public final class CacheConstants {

  public static final String LOCKOUT_ATTEMPT_PREFIX = "lockout:attempt:";
  public static final String LOCKOUT_LOCKED_PREFIX = "lockout:locked:";
  public static final String RATE_LIMIT_PREFIX = "rate:";

  private CacheConstants() {}
}
