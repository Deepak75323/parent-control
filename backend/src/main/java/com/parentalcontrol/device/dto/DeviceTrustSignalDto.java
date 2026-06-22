package com.parentalcontrol.device.dto;

import lombok.Data;

/** Client-reported local trust signals (defense in depth with Play Integrity). */
@Data
public class DeviceTrustSignalDto {
    private Boolean rootDetected;
    private Boolean magiskDetected;
    private Boolean fridaDetected;
    private Boolean emulatorDetected;
    private Boolean debuggerAttached;
    private Boolean hooksDetected;
    private Boolean testKeysBuild;
    private String collectorVersion;
}
