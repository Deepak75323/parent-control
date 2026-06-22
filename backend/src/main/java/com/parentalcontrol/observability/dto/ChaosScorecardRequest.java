package com.parentalcontrol.observability.dto;

import lombok.Data;

import java.util.List;

@Data
public class ChaosScorecardRequest {
    private String runId;
    private String suite;
    private List<ChaosScenarioResultDto> scenarios;
}
