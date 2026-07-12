package com.parentalcontrol.policy.dto;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class GeofenceSummaryResponse {

  private String policyId;
  private String ruleId;
  private String name;
  private double latitude;
  private double longitude;
  private double radiusM;
  private boolean alertOnEnter;
  private boolean alertOnExit;
}
