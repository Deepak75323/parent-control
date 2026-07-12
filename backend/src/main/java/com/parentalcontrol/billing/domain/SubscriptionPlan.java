package com.parentalcontrol.billing.domain;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum SubscriptionPlan {
  FREE("free", 10, false),
  STANDARD("standard", 10, true),
  FAMILY("family", 10, true);

  private final String planId;
  private final int maxDevices;
  private final boolean liveStreamingAllowed;

  public static SubscriptionPlan fromPlanId(String planId) {
    if (planId == null) {
      return FREE;
    }
    for (SubscriptionPlan plan : values()) {
      if (plan.planId.equalsIgnoreCase(planId)) {
        return plan;
      }
    }
    return FREE;
  }
}
