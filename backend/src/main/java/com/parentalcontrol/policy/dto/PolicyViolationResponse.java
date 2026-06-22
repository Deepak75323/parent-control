package com.parentalcontrol.policy.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PolicyViolationResponse {

    private UUID id;
    private UUID deviceId;
    private UUID policyId;
    private String violationType;
    private String packageName;
    private Map<String, Object> details;
    private Instant occurredAt;
}
