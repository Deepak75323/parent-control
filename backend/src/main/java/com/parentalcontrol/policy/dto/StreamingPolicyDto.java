package com.parentalcontrol.policy.dto;

import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

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
