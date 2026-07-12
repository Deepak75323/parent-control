package com.parentalcontrol.billing.dto;

import java.time.Instant;
import java.util.UUID;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class SubscriptionResponse {
  private UUID id;
  private String planId;
  private String status;
  private boolean active;
  private Instant trialEndsAt;
  private Instant currentPeriodEnd;
  private int maxDevices;
  private boolean liveStreamingAllowed;
}
