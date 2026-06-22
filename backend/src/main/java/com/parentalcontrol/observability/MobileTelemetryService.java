package com.parentalcontrol.observability;

import com.parentalcontrol.observability.dto.MobileTelemetryBatchRequest;
import com.parentalcontrol.observability.dto.MobileTelemetryEventDto;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class MobileTelemetryService {

    private final MeterRegistry meterRegistry;
    private final DeviceHealthObservabilityService deviceHealthObservabilityService;

    public void ingest(MobileTelemetryBatchRequest batch) {
        if (batch == null || batch.getEvents() == null) {
            return;
        }
        for (MobileTelemetryEventDto event : batch.getEvents()) {
            ingestEvent(event);
        }
    }

    private void ingestEvent(MobileTelemetryEventDto event) {
        if (event == null || event.getEvent() == null) {
            return;
        }
        String category = event.getCategory() != null ? event.getCategory() : "mobile";
        String name = event.getEvent();
        meterRegistry.counter("mobile.telemetry.events.total", "category", category, "event", name).increment();

        switch (name) {
            case "recovery" -> {
                boolean success = attributeBool(event, "success", true);
                String trigger = attributeString(event, "trigger");
                deviceHealthObservabilityService.recordBootRecovery(success, trigger);
            }
            case "phase_success" -> {
                String phase = attributeString(event, "phase");
                long lagMs = attributeLong(event, "commandLagMs", 0L);
                deviceHealthObservabilityService.recordBootPhase(phase, true, lagMs);
                deviceHealthObservabilityService.recordBootRecovery(true, "phase_" + phase);
            }
            case "phase_failed" -> {
                String phase = attributeString(event, "phase");
                long lagMs = attributeLong(event, "commandLagMs", 0L);
                deviceHealthObservabilityService.recordBootPhase(phase, false, lagMs);
                deviceHealthObservabilityService.recordBootRecovery(false, "phase_" + phase);
            }
            case "boot_received" -> meterRegistry.counter(
                    "device.boot.received.total",
                    "trigger", attributeString(event, "trigger")).increment();
            case "dead_letter" -> meterRegistry.counter("command.failure.total", "command_type", "remote", "reason", "dead_letter")
                    .increment();
            case "enqueued" -> meterRegistry.counter("command.sent.total", "command_type", "remote_buffered").increment();
            case "state_change" -> {
                if ("stomp_ownership".equals(category)) {
                    meterRegistry.counter(
                            "stomp.ownership.state.total",
                            "state", attributeString(event, "state"),
                            "connected", String.valueOf(attributeBool(event, "connected", false)))
                            .increment();
                }
            }
            case "message_deduplicated" -> meterRegistry.counter(
                    "stomp.ownership.dedup.total",
                    "destination", attributeString(event, "destination")).increment();
            case "ice_restart_attempt", "ice_renegotiate_attempt" -> meterRegistry.counter(
                    "stream.ice.recovery.total",
                    "event", name,
                    "role", attributeString(event, "role")).increment();
            case "ice_recovered" -> meterRegistry.counter(
                    "stream.ice.recovered.total",
                    "role", attributeString(event, "role")).increment();
            case "ice_recovery_failed", "session_orphan", "watchdog_orphan_native" -> meterRegistry.counter(
                    "stream.session.failure.total",
                    "event", name).increment();
            default -> log.debug("mobile telemetry category={} event={}", category, name);
        }
    }

    private static boolean attributeBool(MobileTelemetryEventDto event, String key, boolean defaultValue) {
        if (event.getAttributes() == null || !event.getAttributes().containsKey(key)) {
            return defaultValue;
        }
        Object v = event.getAttributes().get(key);
        return v instanceof Boolean b ? b : Boolean.parseBoolean(String.valueOf(v));
    }

    private static String attributeString(MobileTelemetryEventDto event, String key) {
        if (event.getAttributes() == null) {
            return null;
        }
        Object v = event.getAttributes().get(key);
        return v != null ? String.valueOf(v) : null;
    }

    private static long attributeLong(MobileTelemetryEventDto event, String key, long defaultValue) {
        if (event.getAttributes() == null || !event.getAttributes().containsKey(key)) {
            return defaultValue;
        }
        Object v = event.getAttributes().get(key);
        if (v instanceof Number n) {
            return n.longValue();
        }
        try {
            return Long.parseLong(String.valueOf(v));
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }
}
