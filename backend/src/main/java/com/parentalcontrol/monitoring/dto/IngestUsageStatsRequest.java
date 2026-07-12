package com.parentalcontrol.monitoring.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import java.util.List;
import lombok.Data;

@Data
public class IngestUsageStatsRequest {

  @NotEmpty
  @Size(max = 200)
  @Valid
  private List<UsageStatsItemRequest> entries;
}
