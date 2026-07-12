package com.parentalcontrol.observability.dto;

import lombok.Data;

@Data
public class CertificationScenarioResultDto {
  private String id;
  private String name;
  private String status;
  private Integer mttrSeconds;
  private String evidence;
}
