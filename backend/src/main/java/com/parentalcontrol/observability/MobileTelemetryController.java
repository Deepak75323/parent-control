package com.parentalcontrol.observability;

import com.parentalcontrol.common.api.ApiResponse;
import com.parentalcontrol.observability.dto.MobileTelemetryBatchRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/device/telemetry")
@RequiredArgsConstructor
public class MobileTelemetryController {

    private final MobileTelemetryService mobileTelemetryService;

    @PostMapping("/batch")
    public ApiResponse<Void> ingestBatch(@RequestBody MobileTelemetryBatchRequest request) {
        mobileTelemetryService.ingest(request);
        return ApiResponse.ok(null);
    }
}
