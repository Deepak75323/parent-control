package com.parentalcontrol.device.security;

import java.util.List;
import lombok.Builder;

@Builder
public record IntegrityVerdict(
    String packageName,
    String appRecognitionVerdict,
    List<String> deviceRecognitionVerdicts,
    String appLicensingVerdict,
    String requestNonce,
    boolean playRecognized,
    boolean meetsDeviceIntegrity,
    boolean meetsStrongIntegrity,
    boolean meetsBasicIntegrity) {}
