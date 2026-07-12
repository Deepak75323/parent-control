package com.parentalcontrol.policy.dto;

import com.parentalcontrol.policy.domain.TimeExtensionStatus;
import java.time.Instant;
import java.util.UUID;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class TimeExtensionResponse {

  private UUID id;
  private UUID deviceId;
  private String deviceName;
  private String packageName;
  private String appLabel;
  private int requestedMinutes;
  private TimeExtensionStatus status;
  private Integer grantMinutes;
  private Instant grantedUntil;
  private Instant createdAt;
  private Instant respondedAt;
}
