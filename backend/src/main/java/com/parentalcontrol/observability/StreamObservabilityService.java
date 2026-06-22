package com.parentalcontrol.observability;

import com.parentalcontrol.monitoring.domain.ScreenSession;
import com.parentalcontrol.monitoring.domain.ScreenSessionStatus;
import com.parentalcontrol.monitoring.repository.ScreenSessionRepository;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

@Service
@RequiredArgsConstructor
public class StreamObservabilityService {

    private final MeterRegistry meterRegistry;
    private final ScreenSessionRepository screenSessionRepository;
    private final Map<String, Timer> startLatencyTimers = new ConcurrentHashMap<>();
    private final AtomicLong activeSessions = new AtomicLong();

    @PostConstruct
    void registerGauges() {
        Gauge.builder("stream.active.sessions", activeSessions, AtomicLong::get).register(meterRegistry);
    }

    public void recordStreamStart(String streamType) {
        String type = normalize(streamType);
        meterRegistry.counter("stream.start.total", "stream_type", type).increment();
    }

    public void recordStreamActive(ScreenSession session) {
        if (session.getStartedAt() == null) {
            return;
        }
        double seconds = Duration.between(session.getStartedAt(), Instant.now()).toMillis() / 1000.0;
        startLatencyTimer(session.getStreamType()).record(Duration.ofMillis((long) (seconds * 1000)));
        meterRegistry.counter("stream.start.success.total", "stream_type", normalize(session.getStreamType()))
                .increment();
    }

    public void recordStreamFailure(String streamType, String reason) {
        meterRegistry.counter(
                "stream.failure.total",
                "stream_type", normalize(streamType),
                "reason", normalizeReason(reason)).increment();
    }

    public void refreshActiveSessionGauge() {
        long count = screenSessionRepository.countByStatus(ScreenSessionStatus.ACTIVE);
        activeSessions.set(count);
    }

    private Timer startLatencyTimer(String streamType) {
        String type = normalize(streamType);
        return startLatencyTimers.computeIfAbsent(type, t ->
                Timer.builder("stream.start.latency.seconds")
                        .tag("stream_type", t)
                        .publishPercentiles(0.5, 0.95, 0.99)
                        .register(meterRegistry));
    }

    private static String normalize(String streamType) {
        return streamType == null || streamType.isBlank() ? "unknown" : streamType.toLowerCase();
    }

    private static String normalizeReason(String reason) {
        if (reason == null || reason.isBlank()) {
            return "unknown";
        }
        String n = reason.toLowerCase().replaceAll("[^a-z0-9]+", "_");
        return n.length() > 48 ? n.substring(0, 48) : n;
    }
}
