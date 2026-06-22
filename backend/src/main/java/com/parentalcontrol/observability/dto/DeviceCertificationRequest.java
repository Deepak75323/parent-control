package com.parentalcontrol.observability.dto;

import lombok.Data;

import java.util.List;

@Data
public class DeviceCertificationRequest {
    private String deviceId;
    private String vendor;
    private String status;
    private Integer score;
    private String runId;
    private List<CertificationScenarioResultDto> scenarios;
}
