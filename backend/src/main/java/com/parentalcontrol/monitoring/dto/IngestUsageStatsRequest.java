package com.parentalcontrol.monitoring.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import lombok.Data;

import java.util.List;

@Data
public class IngestUsageStatsRequest {

    @NotEmpty
    @Valid
    private List<UsageStatsItemRequest> entries;
}
