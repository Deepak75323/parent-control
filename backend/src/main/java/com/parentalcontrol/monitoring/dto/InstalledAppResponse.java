package com.parentalcontrol.monitoring.dto;

import java.time.Instant;
import java.util.UUID;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class InstalledAppResponse {

  private UUID id;
  private String packageName;
  private String appLabel;
  private String appCategory;
  /** Optional small PNG as base64 (no data-URL prefix). */
  private String iconBase64;
  private Instant syncedAt;
}
