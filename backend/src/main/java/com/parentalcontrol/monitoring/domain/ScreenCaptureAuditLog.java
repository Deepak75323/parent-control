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
@Table(name = "screen_capture_audit_log")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ScreenCaptureAuditLog extends BaseEntity {

  @Column(name = "device_id", nullable = false)
  private UUID deviceId;

  @Column(name = "session_id")
  private UUID sessionId;

  @Column(name = "stream_type", nullable = false)
  private String streamType;

  @Column(name = "capture_mode", nullable = false)
  private String captureMode;

  @Column(name = "parent_user_id")
  private UUID parentUserId;

  @Column(name = "started_at", nullable = false)
  private Instant startedAt;

  @Column(name = "ended_at")
  private Instant endedAt;
}
