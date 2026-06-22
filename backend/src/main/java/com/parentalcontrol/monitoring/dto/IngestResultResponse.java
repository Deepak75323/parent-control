package com.parentalcontrol.monitoring.dto;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class IngestResultResponse {

    private int accepted;
}
