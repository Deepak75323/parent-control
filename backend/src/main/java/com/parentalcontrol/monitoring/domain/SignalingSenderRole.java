package com.parentalcontrol.monitoring.domain;

/** Signaling sender role on the wire ({@link #wireValue()}). */
public enum SignalingSenderRole {
  PARENT("PARENT"),
  CHILD("CHILD");

  private final String wireValue;

  SignalingSenderRole(String wireValue) {
    this.wireValue = wireValue;
  }

  public String wireValue() {
    return wireValue;
  }

  public boolean matches(String raw) {
    return wireValue.equals(raw);
  }
}
