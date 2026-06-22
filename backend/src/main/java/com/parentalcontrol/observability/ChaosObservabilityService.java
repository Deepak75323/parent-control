package com.parentalcontrol.observability;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/** Metrics for chaos experiments and reliability validation runs. */
@Service
@RequiredArgsConstructor
public class ChaosObservabilityService {

    private final MeterRegistry meterRegistry;
    private final Map<String, Timer> recoveryTimers = new ConcurrentHashMap<>();

    public void recordExperiment(String scenarioId, String status, long durationMs) {
        meterRegistry.counter(
                "chaos.experiment.total",
                "scenario", scenarioId,
                "status", status).increment();
        recoveryTimer(scenarioId).record(Duration.ofMillis(durationMs));
    }

    public void recordRecovery(String domain, boolean success, long mttrMs) {
        meterRegistry.counter(
                "chaos.recovery.total",
                "domain", domain,
                "success", String.valueOf(success)).increment();
        meterRegistry.summary("chaos.recovery.mttr.seconds").record(mttrMs / 1000.0);
    }

    private Timer recoveryTimer(String scenarioId) {
        return recoveryTimers.computeIfAbsent(scenarioId, id ->
                Timer.builder("chaos.recovery.duration.seconds")
                        .tag("scenario", id)
                        .publishPercentiles(0.5, 0.95, 0.99)
                        .register(meterRegistry));
    }
}
