package com.parentalcontrol.device.dto;

import com.parentalcontrol.device.domain.DeviceStatus;
import com.parentalcontrol.device.domain.DeviceTrustLevel;
import lombok.Builder;
import lombok.Data;

import java.time.Instant;
import java.util.UUID;

@Data
@Builder
public class DeviceResponse {
    private UUID id;
    private String deviceName;
    private String deviceModel;
    private DeviceStatus status;
    private Integer batteryLevel;
    private Boolean charging;
    private String networkType;
    private Instant lastSeenAt;
    private Double lastLatitude;
    private Double lastLongitude;
    private String foregroundApp;
    private UUID childProfileId;
    private boolean deviceOwnerActive;
    private boolean deviceAdminActive;
    private boolean mediaProjectionGranted;
    private String mediaProjectionConsentState;
    private boolean silentScreenCapable;
    private String protectionLevel;
    private boolean kioskModeActive;
    private boolean accessibilityEnabled;
    private boolean accessibilityServiceConnected;
    private DeviceTrustLevel trustLevel;
    private Integer trustScore;
    private Instant lastTrustEvaluatedAt;
    private String oemVendor;
    private Integer deviceReadinessScore;
    private Integer android16ReadinessScore;
    private Boolean oemSetupComplete;
    private String certificationStatus;
    private Integer certificationScore;
    private Instant lastCertificationAt;
}
