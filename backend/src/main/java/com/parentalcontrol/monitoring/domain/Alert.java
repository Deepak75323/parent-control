package com.parentalcontrol.monitoring.domain;

import com.parentalcontrol.common.domain.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "alerts")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Alert extends BaseEntity {

  @Column(name = "device_id", nullable = false)
  private UUID deviceId;

  @Column(name = "alert_type", nullable = false)
  private String alertType;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false)
  @Builder.Default
  private AlertSeverity severity = AlertSeverity.INFO;

  @Column(nullable = false)
  private String title;

  private String message;

  @JdbcTypeCode(SqlTypes.JSON)
  @Column(name = "payload_json")
  private Map<String, Object> payloadJson;

  @Column(name = "read_at")
  private Instant readAt;
}
