package com.parentalcontrol.monitoring.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import lombok.Data;

@Data
public class NotificationItemRequest {

  @Size(max = 256)
  private String packageName;

  @Size(max = 512)
  private String title;

  @Size(max = 4096)
  private String body;

  @NotNull private Instant postedAt;
}
