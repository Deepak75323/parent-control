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
public class GeofencePolicyDto {

    private UUID policyId;
    private UUID ruleId;
    private String name;
    private double latitude;
    private double longitude;
    private double radiusM;
    private boolean alertOnEnter;
    private boolean alertOnExit;
    private boolean enabled;
}
