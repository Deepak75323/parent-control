package com.parentalcontrol.observability;

/** Coarse stream funnel stages for Prometheus / support triage (no ADB required). */
public enum StreamFunnelStage {
  REQUEST_SENT,
  CHILD_OFFER,
  PARENT_ANSWER,
  CONNECTED,
  FAILED
}
