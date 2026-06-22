package com.parentalcontrol.observability;

import com.parentalcontrol.device.domain.Device;
import com.parentalcontrol.device.domain.DeviceStatus;
import com.parentalcontrol.device.domain.DeviceTrustLevel;
import com.parentalcontrol.device.repository.DeviceRepository;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.atomic.AtomicLong;

@Service
@RequiredArgsConstructor
public class DeviceHealthObservabilityService {

    private final MeterRegistry meterRegistry;
    private final DeviceRepository deviceRepository;

    private final AtomicLong onlineCount = new AtomicLong();
    private final AtomicLong offlineCount = new AtomicLong();
    private final AtomicLong projectionGrantedCount = new AtomicLong();
    private final AtomicLong suspiciousCount = new AtomicLong();
    private final AtomicLong maxHeartbeatLagSeconds = new AtomicLong();

    @PostConstruct
    void registerGauges() {
        Gauge.builder("device.online.count", onlineCount, AtomicLong::get).register(meterRegistry);
        Gauge.builder("device.offline.count", offlineCount, AtomicLong::get).register(meterRegistry);
        Gauge.builder("device.media_projection.granted.count", projectionGrantedCount, AtomicLong::get)
                .register(meterRegistry);
        Gauge.builder("device.suspicious.count", suspiciousCount, AtomicLong::get).register(meterRegistry);
        Gauge.builder("device.heartbeat.freshness.max_lag_seconds", maxHeartbeatLagSeconds, AtomicLong::get)
                .description("Worst-case heartbeat staleness across fleet (seconds)")
                .register(meterRegistry);
    }

    public void recordHeartbeat(Device device) {
        meterRegistry.counter("device.heartbeat.received.total").increment();
        if (device.getLastSeenAt() != null) {
            double lag = Duration.between(device.getLastSeenAt(), Instant.now()).toMillis() / 1000.0;
            meterRegistry.summary("device.heartbeat.lag.seconds").record(Math.max(0, lag));
        }
    }

    public void recordBootRecovery(boolean success, String trigger) {
        meterRegistry.counter(
                "device.boot.recovery.total",
                "success", String.valueOf(success),
                "trigger", trigger != null ? trigger : "unknown").increment();
    }

    public void recordBootPhase(String phase, boolean success, long commandLagMs) {
        meterRegistry.counter(
                "device.boot.phase.total",
                "phase", phase != null ? phase : "unknown",
                "success", String.valueOf(success)).increment();
        if (commandLagMs > 0) {
            meterRegistry.summary("device.boot.phase.command_lag_ms", "phase", phase != null ? phase : "unknown")
                    .record(commandLagMs);
        }
    }

    public void recordMediaProjectionState(boolean granted) {
        meterRegistry.counter(
                "device.media_projection.state.total",
                "granted", String.valueOf(granted)).increment();
    }

    public void refreshFleetGauges() {
        var devices = deviceRepository.findAll();
        long online = 0;
        long offline = 0;
        long projection = 0;
        long suspicious = 0;
        long maxLag = 0;
        Instant now = Instant.now();

        for (Device device : devices) {
            if (device.getStatus() == DeviceStatus.ONLINE) {
                online++;
            } else {
                offline++;
            }
            if (device.isMediaProjectionGranted()) {
                projection++;
            }
            DeviceTrustLevel trust = device.getTrustLevel();
            if (trust == DeviceTrustLevel.ELEVATED_RISK
                    || trust == DeviceTrustLevel.UNTRUSTED
                    || trust == DeviceTrustLevel.BLOCKED) {
                suspicious++;
            }
            if (device.getLastSeenAt() != null) {
                long lag = Duration.between(device.getLastSeenAt(), now).getSeconds();
                maxLag = Math.max(maxLag, Math.max(0, lag));
            }
        }

        onlineCount.set(online);
        offlineCount.set(offline);
        projectionGrantedCount.set(projection);
        suspiciousCount.set(suspicious);
        maxHeartbeatLagSeconds.set(maxLag);
    }
}
