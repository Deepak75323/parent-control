package com.parentalcontrol.policy.domain;

import com.parentalcontrol.common.domain.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "time_extension_requests")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TimeExtensionRequest extends BaseEntity {

  @Column(name = "device_id", nullable = false)
  private UUID deviceId;

  @Column(name = "parent_profile_id", nullable = false)
  private UUID parentProfileId;

  @Column(name = "package_name", nullable = false)
  private String packageName;

  @Column(name = "app_label")
  private String appLabel;

  @Column(name = "requested_minutes", nullable = false)
  private Integer requestedMinutes;

  @Enumerated(EnumType.STRING)
  @Column(name = "status", nullable = false)
  @Builder.Default
  private TimeExtensionStatus status = TimeExtensionStatus.PENDING;

  @Column(name = "grant_minutes")
  private Integer grantMinutes;

  @Column(name = "granted_until")
  private Instant grantedUntil;

  @Column(name = "responded_at")
  private Instant respondedAt;
}
