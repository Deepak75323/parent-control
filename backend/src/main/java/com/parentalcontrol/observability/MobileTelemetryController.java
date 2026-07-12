package com.parentalcontrol.observability;

import com.parentalcontrol.common.api.ApiResponse;
import com.parentalcontrol.common.constant.ApiConstants;
import com.parentalcontrol.observability.dto.MobileTelemetryBatchRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping(ApiConstants.DEVICE_TELEMETRY)
@RequiredArgsConstructor
public class MobileTelemetryController {

  private final MobileTelemetryService mobileTelemetryService;

  @PostMapping("/batch")
  public ApiResponse<Void> ingestBatch(@Valid @RequestBody MobileTelemetryBatchRequest request) {
    mobileTelemetryService.ingest(request);
    return ApiResponse.ok(null);
  }
}
