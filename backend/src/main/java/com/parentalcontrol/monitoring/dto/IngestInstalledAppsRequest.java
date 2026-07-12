package com.parentalcontrol.monitoring.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import java.util.List;
import lombok.Data;

@Data
public class IngestInstalledAppsRequest {

  @NotEmpty
  @Size(max = 500)
  @Valid
  private List<InstalledAppItemRequest> apps;
}
