package com.parentalcontrol.device.dto;

import com.parentalcontrol.device.domain.DeviceStatus;
import com.parentalcontrol.device.domain.DeviceTrustLevel;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import lombok.Builder;
import lombok.Data;

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
  private Boolean monitoringDegraded;
  private String monitoringMode;
  private String standbyBucket;
  /** Child heartbeat OEM diagnostics — surfaced for parent readiness checklist. */
  private Boolean permBattery;
  private Boolean permAutostart;
  private Boolean permFullScreenIntent;
  private Boolean permNotifications;
  private Boolean permCamera;
  private Boolean permMicrophone;
  private Boolean permOverlay;
  private Boolean permAccessibility;
  private List<String> readinessFailureTags;
}
