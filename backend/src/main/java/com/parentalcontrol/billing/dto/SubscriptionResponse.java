package com.parentalcontrol.billing.dto;

import lombok.Builder;
import lombok.Data;

import java.time.Instant;
import java.util.UUID;

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
