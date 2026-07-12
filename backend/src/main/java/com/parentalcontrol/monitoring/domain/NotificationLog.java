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
@Table(name = "notification_logs")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class NotificationLog extends BaseEntity {

  @Column(name = "device_id", nullable = false)
  private UUID deviceId;

  @Column(name = "package_name")
  private String packageName;

  private String title;

  private String body;

  @Column(name = "posted_at", nullable = false)
  private Instant postedAt;
}
