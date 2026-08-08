package com.parentalcontrol.device.dto;

import com.parentalcontrol.device.domain.DeviceStatus;
import java.time.Instant;
import java.util.UUID;
import lombok.Builder;
import lombok.Value;

/**
 * Result of a parent-initiated FCM wake for a child device.
 *
 * <p>Returned by {@code POST /v1/devices/{deviceId}/wake}. Parent uses {@link #lastSeenAt} and
 * {@link #status} as the baseline to poll against — the wake succeeded when a subsequent
 * {@code GET /devices/{deviceId}} shows a newer {@code lastSeenAt}.
 */
@Value
@Builder
public class DeviceWakeResponse {
  UUID deviceId;
  boolean fcmDispatched;
  DeviceStatus status;
  Instant lastSeenAt;
}
