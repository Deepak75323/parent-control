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
@Table(name = "location_history")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LocationHistory extends BaseEntity {

  @Column(name = "device_id", nullable = false)
  private UUID deviceId;

  @Column(nullable = false)
  private Double latitude;

  @Column(nullable = false)
  private Double longitude;

  @Column(name = "accuracy_m")
  private Double accuracyM;

  @Column(name = "recorded_at", nullable = false)
  private Instant recordedAt;
}
