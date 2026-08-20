package com.parentalcontrol.analytics.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import java.util.List;
import lombok.Data;

@Data
public class ClientAnalyticsBatchRequest {

  @Size(max = 64)
  private String correlationId;

  @Size(max = 64)
  private String collectorVersion;

  @NotEmpty
  @Size(max = 100)
  @Valid
  private List<ClientAnalyticsEventDto> events;
}
