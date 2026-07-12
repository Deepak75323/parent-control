package com.parentalcontrol.observability.dto;

import java.util.List;
import lombok.Data;

@Data
public class DeviceCertificationRequest {
  private String deviceId;
  private String vendor;
  private String status;
  private Integer score;
  private String runId;
  private List<CertificationScenarioResultDto> scenarios;
}
