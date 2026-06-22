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
public class StreamingPolicyDto {

    private UUID policyId;
    private boolean screenShareAllowed;
    private boolean cameraAllowed;
    private boolean audioAllowed;
    private boolean enabled;
}
