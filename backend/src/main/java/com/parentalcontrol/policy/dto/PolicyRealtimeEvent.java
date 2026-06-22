package com.parentalcontrol.policy.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PolicyRealtimeEvent {

    private String eventType;
    private UUID deviceId;
    private String policyVersion;
    private String message;
}
