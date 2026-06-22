package com.parentalcontrol.observability;

import com.parentalcontrol.device.domain.DeviceCommand;
import com.parentalcontrol.device.domain.DeviceCommandType;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

@Service
@RequiredArgsConstructor
public class CommandObservabilityService {

    private final MeterRegistry meterRegistry;
    private final ConcurrentMap<String, Timer> lagTimers = new ConcurrentHashMap<>();

    public void recordSent(DeviceCommandType commandType) {
        meterRegistry.counter("command.sent.total", "command_type", commandType.name()).increment();
    }

    public void recordDelivered(DeviceCommandType commandType) {
        meterRegistry.counter("command.delivered.total", "command_type", commandType.name()).increment();
    }

    public void recordExecuted(DeviceCommandType commandType, boolean success) {
        meterRegistry.counter(
                "command.executed.total",
                "command_type", commandType.name(),
                "success", String.valueOf(success)).increment();
        if (!success) {
            meterRegistry.counter("command.failure.total", "command_type", commandType.name(), "reason", "execution")
                    .increment();
        }
    }

    public void recordAcknowledged(DeviceCommand command, boolean success) {
        DeviceCommandType type = command.getCommandType();
        if (success) {
            recordExecuted(type, true);
        } else {
            meterRegistry.counter(
                    "command.failure.total",
                    "command_type", type.name(),
                    "reason", normalizeReason(command.getErrorMessage())).increment();
        }
        recordLag(command);
    }

    public void recordFailed(DeviceCommandType commandType, String reason) {
        meterRegistry.counter(
                "command.failure.total",
                "command_type", commandType.name(),
                "reason", normalizeReason(reason)).increment();
    }

    public void recordLag(DeviceCommand command) {
        Instant end = command.getCompletedAt() != null ? command.getCompletedAt() : Instant.now();
        if (command.getCreatedAt() == null) {
            return;
        }
        double seconds = Duration.between(command.getCreatedAt(), end).toMillis() / 1000.0;
        lagTimer(command.getCommandType().name()).record(Duration.ofMillis((long) (seconds * 1000)));
    }

    private Timer lagTimer(String commandType) {
        return lagTimers.computeIfAbsent(commandType, type ->
                Timer.builder("command.lag.seconds")
                        .tag("command_type", type)
                        .publishPercentiles(0.5, 0.95, 0.99)
                        .register(meterRegistry));
    }

    private static String normalizeReason(String reason) {
        if (reason == null || reason.isBlank()) {
            return "unknown";
        }
        String normalized = reason.toLowerCase().replaceAll("[^a-z0-9]+", "_");
        return normalized.length() > 48 ? normalized.substring(0, 48) : normalized;
    }
}
