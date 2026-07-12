package com.parentalcontrol.monitoring.dto;

import java.time.Instant;
import java.util.UUID;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class LocationHistoryResponse {

  private UUID id;
  private Double latitude;
  private Double longitude;
  private Double accuracyM;
  private Instant recordedAt;
}
