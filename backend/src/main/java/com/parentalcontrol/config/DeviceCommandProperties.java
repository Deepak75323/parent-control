package com.parentalcontrol.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Data
@ConfigurationProperties(prefix = "app.device-command")
public class DeviceCommandProperties {

  /** Max times a command may enter DELIVERED before stale timeout permanently fails it. */
  private int maxDeliveryAttempts = 3;

  /** Seconds in DELIVERED without ACK before requeue (or fail when attempts exhausted). */
  private long staleDeliveredSeconds = 300;

  /**
   * Seconds a DELIVERED command with an execution fingerprint may wait for ACK before
   * auto-completing from the recorded execution outcome.
   */
  private long staleExecutedAwaitingAckSeconds = 3600;
}
