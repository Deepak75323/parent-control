package com.parentalcontrol.policy.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PolicyBundleResponse {

    private String policyVersion;
    private UUID deviceId;
    private List<AppBlockPolicyDto> appBlocks;
    private List<ScreenTimePolicyDto> screenTime;
    private List<DeviceSchedulePolicyDto> deviceSchedules;
    private List<GeofencePolicyDto> geofences;
    private StreamingPolicyDto streaming;
    private List<TimeGrantDto> timeGrants;
}
