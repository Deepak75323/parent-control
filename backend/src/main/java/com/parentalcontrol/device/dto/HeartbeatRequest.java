package com.parentalcontrol.device.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class HeartbeatRequest {

  @Min(0)
  @Max(100)
  private Integer batteryLevel;

  private Boolean charging;

  @Size(max = 32)
  private String networkType;

  @Min(-90)
  @Max(90)
  private Double latitude;

  @Min(-180)
  @Max(180)
  private Double longitude;

  @Size(max = 256)
  private String foregroundApp;

  @Size(max = 512)
  private String fcmToken;

  @Size(max = 8192)
  private String integrityToken;

  @Valid private DeviceTrustSignalDto trustSignals;

  private Boolean deviceOwnerActive;
  private Boolean deviceAdminActive;
  private Boolean mediaProjectionGranted;

  @Size(max = 64)
  private String mediaProjectionConsentState;

  private Boolean silentScreenCapable;

  @Size(max = 64)
  private String protectionLevel;

  private Boolean kioskModeActive;
  private Boolean accessibilityEnabled;
  private Boolean accessibilityServiceConnected;

  @Size(max = 64)
  private String oemVendor;

  @Min(1)
  @Max(100)
  private Integer androidSdkInt;

  @Min(0)
  @Max(100)
  private Integer deviceReadinessScore;

  @Min(0)
  @Max(100)
  private Integer android16ReadinessScore;

  private Boolean oemSetupComplete;

  @Size(max = 32)
  private java.util.Map<String, Object> oemDiagnostics;
}
