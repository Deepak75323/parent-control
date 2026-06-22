package com.parentalcontrol.device.service;

import com.parentalcontrol.device.domain.DeviceTrustLevel;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class DeviceTrustObservabilityService {

    private final MeterRegistry meterRegistry;

    public void recordPairingDenied(DeviceTrustLevel level, int score) {
        meterRegistry.counter("device.trust.pairing.denied", "level", level.name())
                .increment();
        meterRegistry.summary("device.trust.pairing.denied.score").record(score);
    }

    public void recordSessionDenied(UUID deviceId, DeviceTrustLevel level, int score) {
        meterRegistry.counter("device.trust.session.denied", "level", level.name())
                .increment();
        meterRegistry.summary("device.trust.session.denied.score").record(score);
    }

    public void recordElevatedRisk(UUID deviceId) {
        meterRegistry.counter("device.trust.elevated_risk").increment();
    }

    public void recordInterceptorBlocked(DeviceTrustLevel level) {
        meterRegistry.counter("device.trust.interceptor.blocked", "level", level.name())
                .increment();
    }
}
