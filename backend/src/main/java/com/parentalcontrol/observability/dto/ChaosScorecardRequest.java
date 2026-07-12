package com.parentalcontrol.observability.dto;

import java.util.List;
import lombok.Data;

@Data
public class ChaosScorecardRequest {
  private String runId;
  private String suite;
  private List<ChaosScenarioResultDto> scenarios;
}
