package com.parentalcontrol.policy.dto;

import com.parentalcontrol.policy.domain.PolicyType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PolicyResponse {

    private UUID id;
    private String name;
    private PolicyType policyType;
    private boolean enabled;
    private int version;
    private List<UUID> deviceIds;
    private Instant updatedAt;
}
