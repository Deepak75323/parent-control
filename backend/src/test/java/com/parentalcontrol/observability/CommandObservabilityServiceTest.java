package com.parentalcontrol.observability;

import static org.assertj.core.api.Assertions.assertThat;

import com.parentalcontrol.device.domain.DeviceCommandType;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class CommandObservabilityServiceTest {

  private SimpleMeterRegistry registry;
  private CommandObservabilityService service;

  @BeforeEach
  void setUp() {
    registry = new SimpleMeterRegistry();
    service = new CommandObservabilityService(registry);
  }

  @Test
  void recordsCommandLifecycleCounters() {
    service.recordSent(DeviceCommandType.ENABLE_KIOSK);
    service.recordDelivered(DeviceCommandType.ENABLE_KIOSK);
    service.recordExecuted(DeviceCommandType.ENABLE_KIOSK, true);

    assertThat(
            registry
                .get("command.sent.total")
                .tag("command_type", "ENABLE_KIOSK")
                .counter()
                .count())
        .isEqualTo(1.0);
    assertThat(
            registry
                .get("command.delivered.total")
                .tag("command_type", "ENABLE_KIOSK")
                .counter()
                .count())
        .isEqualTo(1.0);
    assertThat(
            registry
                .get("command.executed.total")
                .tag("command_type", "ENABLE_KIOSK")
                .tag("success", "true")
                .counter()
                .count())
        .isEqualTo(1.0);
  }
}
