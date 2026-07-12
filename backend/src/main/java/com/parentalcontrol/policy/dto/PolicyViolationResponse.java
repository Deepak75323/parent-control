package com.parentalcontrol.policy.dto;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

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
