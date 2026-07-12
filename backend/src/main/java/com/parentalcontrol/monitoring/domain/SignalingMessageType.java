package com.parentalcontrol.monitoring.domain;

/** WebRTC signaling message types on the wire ({@link #wireValue()}). */
public enum SignalingMessageType {
  OFFER("offer"),
  ANSWER("answer"),
  ICE("ice");

  private final String wireValue;

  SignalingMessageType(String wireValue) {
    this.wireValue = wireValue;
  }

  public String wireValue() {
    return wireValue;
  }

  public boolean matches(String raw) {
    return wireValue.equals(raw);
  }

  public static boolean isKnown(String raw) {
    if (raw == null) {
      return false;
    }
    for (SignalingMessageType type : values()) {
      if (type.wireValue.equals(raw)) {
        return true;
      }
    }
    return false;
  }
}
