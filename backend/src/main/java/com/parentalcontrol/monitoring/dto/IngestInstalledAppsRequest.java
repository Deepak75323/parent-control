package com.parentalcontrol.monitoring.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import lombok.Data;

import java.util.List;

@Data
public class IngestInstalledAppsRequest {

    @NotEmpty
    @Valid
    private List<InstalledAppItemRequest> apps;
}
