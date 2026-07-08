package com.parentalcontrol.device.dto;

import lombok.Data;

@Data
public class HeartbeatRequest {
    private Integer batteryLevel;
    private Boolean charging;
    private String networkType;
    private Double latitude;
    private Double longitude;
    private String foregroundApp;
    private String fcmToken;
    private String integrityToken;
    private DeviceTrustSignalDto trustSignals;
    private Boolean deviceOwnerActive;
    private Boolean deviceAdminActive;
    private Boolean mediaProjectionGranted;
    private String mediaProjectionConsentState;
    private Boolean silentScreenCapable;
    private String protectionLevel;
    private Boolean kioskModeActive;
    private Boolean accessibilityEnabled;
    private Boolean accessibilityServiceConnected;
    private String oemVendor;
    private Integer androidSdkInt;
    private Integer deviceReadinessScore;
    private Integer android16ReadinessScore;
    private Boolean oemSetupComplete;
    private java.util.Map<String, Object> oemDiagnostics;
}
