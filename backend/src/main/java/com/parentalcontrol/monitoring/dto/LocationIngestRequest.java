package com.parentalcontrol.monitoring.dto;

import jakarta.validation.constraints.NotNull;
import java.time.Instant;
import lombok.Data;

@Data
public class LocationIngestRequest {

  @NotNull private Double latitude;

  @NotNull private Double longitude;

  private Double accuracyM;

  private Instant recordedAt;
}
