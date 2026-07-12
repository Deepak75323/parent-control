package com.parentalcontrol.monitoring.dto;

import com.parentalcontrol.device.domain.DeviceStatus;
import java.time.Instant;
import java.util.UUID;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class DeviceStatusEvent {

  private UUID deviceId;

  /** Child device label shown in parent UI. */
  private String deviceName;

  private DeviceStatus status;
  private Integer batteryLevel;
  private Boolean charging;
  private String networkType;
  private Double latitude;
  private Double longitude;
  private String foregroundApp;
  private Instant lastSeenAt;
  private boolean mediaProjectionGranted;
  private String mediaProjectionConsentState;
  private boolean silentScreenCapable;
  private String protectionLevel;
}
