package com.parentalcontrol.common.constant;

/** STOMP destination prefixes and messaging constants. */
public final class SocketConstants {

  public static final String TOPIC_PREFIX = "/topic";
  public static final String QUEUE_PREFIX = "/queue";
  public static final String APP_PREFIX = "/app";
  public static final String USER_PREFIX = "/user";

  public static final String TOPIC_DEVICE = TOPIC_PREFIX + "/device.";
  public static final String TOPIC_PARENT = TOPIC_PREFIX + "/parent.";
  public static final String TOPIC_SIGNALING = TOPIC_PREFIX + "/signaling.";

  private SocketConstants() {}
}
