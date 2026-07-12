package com.parentalcontrol.policy.dto;

import jakarta.validation.constraints.NotBlank;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import lombok.Data;

@Data
public class PolicyViolationRequest {

  private UUID policyId;

  @NotBlank private String violationType;

  private String packageName;
  private Map<String, Object> details;
  private Instant occurredAt;
}
