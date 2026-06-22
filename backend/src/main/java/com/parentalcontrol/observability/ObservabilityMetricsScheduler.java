package com.parentalcontrol.observability;

import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class ObservabilityMetricsScheduler {

    private final DeviceHealthObservabilityService deviceHealthObservabilityService;
    private final StreamObservabilityService streamObservabilityService;

    @Scheduled(fixedDelayString = "${app.observability.gauge-refresh-ms:60000}")
    public void refreshGauges() {
        deviceHealthObservabilityService.refreshFleetGauges();
        streamObservabilityService.refreshActiveSessionGauge();
    }
}
