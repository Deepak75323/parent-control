package com.parentalcontrol.monitoring.dto;

import java.time.Instant;
import java.util.UUID;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class NotificationResponse {

  private UUID id;
  private String packageName;
  private String title;
  private String body;
  private Instant postedAt;
}
