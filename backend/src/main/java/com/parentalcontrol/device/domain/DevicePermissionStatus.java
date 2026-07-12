package com.parentalcontrol.device.domain;

import com.parentalcontrol.common.domain.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "device_permission_status")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DevicePermissionStatus extends BaseEntity {

  @Column(name = "device_id", nullable = false, unique = true)
  private UUID deviceId;

  @Column(name = "accessibility_granted", nullable = false)
  @Builder.Default
  private boolean accessibilityGranted = false;

  @Column(name = "notification_listener_granted", nullable = false)
  @Builder.Default
  private boolean notificationListenerGranted = false;

  @Column(name = "usage_stats_granted", nullable = false)
  @Builder.Default
  private boolean usageStatsGranted = false;

  @Column(name = "location_granted", nullable = false)
  @Builder.Default
  private boolean locationGranted = false;

  @Column(name = "device_admin_granted", nullable = false)
  @Builder.Default
  private boolean deviceAdminGranted = false;

  @Column(name = "overlay_granted", nullable = false)
  @Builder.Default
  private boolean overlayGranted = false;
}
