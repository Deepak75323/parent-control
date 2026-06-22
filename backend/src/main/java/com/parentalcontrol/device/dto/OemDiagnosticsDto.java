package com.parentalcontrol.device.dto;

import lombok.Data;

import java.util.List;
import java.util.Map;

@Data
public class OemDiagnosticsDto {
    private String vendor;
    private String vendorDisplay;
    private String manufacturer;
    private Integer sdkInt;
    private Boolean batteryExempt;
    private Boolean autostartPromptDone;
    private Boolean oemSetupComplete;
    private Integer readinessScore;
    private List<String> failureClassifications;
    private Map<String, Object> raw;
}
