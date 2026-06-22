package com.parentalcontrol.observability.dto;

import lombok.Data;

@Data
public class ChaosScenarioResultDto {
    private String id;
    private String name;
    private String status;
    private Long mttrSeconds;
}
