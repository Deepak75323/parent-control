package com.parentalcontrol.observability;

import io.micrometer.core.instrument.MeterRegistry;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class SecurityObservabilityService {

    private final MeterRegistry meterRegistry;

    public void recordIntegrityFailure(String reason) {
        meterRegistry.counter("security.integrity.failures.total", "reason", normalize(reason)).increment();
    }

    public void recordPairingFailure(String reason) {
        meterRegistry.counter("security.pairing.failures.total", "reason", normalize(reason)).increment();
    }

    public void recordHmacFailure(String reason) {
        meterRegistry.counter("security.hmac.failures.total", "reason", normalize(reason)).increment();
    }

    private static String normalize(String reason) {
        if (reason == null || reason.isBlank()) {
            return "unknown";
        }
        return reason.toLowerCase().replaceAll("[^a-z0-9_]+", "_");
    }
}
