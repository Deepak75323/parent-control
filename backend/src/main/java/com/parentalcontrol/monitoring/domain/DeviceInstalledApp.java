package com.parentalcontrol.monitoring.domain;

import com.parentalcontrol.common.domain.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "device_installed_apps")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DeviceInstalledApp extends BaseEntity {

  @Column(name = "device_id", nullable = false)
  private UUID deviceId;

  @Column(name = "package_name", nullable = false)
  private String packageName;

  @Column(name = "app_label", nullable = false)
  private String appLabel;

  @Column(name = "app_category")
  private String appCategory;

  /** Small PNG icon as base64 (no data: prefix), optional. */
  @Column(name = "icon_base64", columnDefinition = "TEXT")
  private String iconBase64;

  @Column(name = "synced_at", nullable = false)
  private Instant syncedAt;
}
