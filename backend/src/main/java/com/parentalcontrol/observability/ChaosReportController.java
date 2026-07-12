package com.parentalcontrol.observability;

import com.parentalcontrol.common.api.ApiResponse;
import com.parentalcontrol.common.constant.ApiConstants;
import com.parentalcontrol.observability.dto.ChaosScorecardRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Ingest chaos run scorecards from CI/device farm for Prometheus correlation. */
@RestController
@RequestMapping(ApiConstants.ADMIN_CHAOS)
@RequiredArgsConstructor
public class ChaosReportController {

  private final ChaosObservabilityService chaosObservabilityService;

  @PostMapping("/scorecard")
  public ApiResponse<Void> ingestScorecard(@RequestBody ChaosScorecardRequest request) {
    if (request.getScenarios() != null) {
      for (var scenario : request.getScenarios()) {
        long mttrMs = scenario.getMttrSeconds() != null ? scenario.getMttrSeconds() * 1000L : 0L;
        chaosObservabilityService.recordExperiment(scenario.getId(), scenario.getStatus(), mttrMs);
        chaosObservabilityService.recordRecovery(
            "platform", "PASS".equals(scenario.getStatus()), mttrMs);
      }
    }
    return ApiResponse.ok(null);
  }
}
