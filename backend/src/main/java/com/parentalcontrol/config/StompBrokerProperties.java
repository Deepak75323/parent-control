package com.parentalcontrol.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * STOMP broker relay settings for horizontal scaling across API replicas. When {@link
 * #relayEnabled} is true, Spring relays /topic and /queue traffic through RabbitMQ's STOMP adapter
 * so all pods share the same logical broker.
 */
@Data
@ConfigurationProperties(prefix = "app.stomp")
public class StompBrokerProperties {

  /** Use RabbitMQ STOMP relay instead of the in-memory simple broker. */
  private boolean relayEnabled = false;

  private Relay relay = new Relay();

  private Channel channel = new Channel();

  @Data
  public static class Relay {
    private String host = "localhost";
    private int port = 61613;
    private String systemLogin = "guest";
    private String systemPasscode = "guest";
    private String clientLogin = "guest";
    private String clientPasscode = "guest";

    /** Required for {@code convertAndSendToUser} across replicas. */
    private String userDestinationBroadcast = "/topic/unresolved-user-destination";

    private String userRegistryBroadcast = "/topic/user-registry";

    /** TCP connect timeout for relay system/client connections (ms). */
    private int connectTimeoutMs = 10_000;

    /** Heartbeat interval negotiated with the relay (ms). */
    private int heartbeatSendIntervalMs = 10_000;

    private int heartbeatReceiveIntervalMs = 10_000;
  }

  @Data
  public static class Channel {
    private int inboundCorePoolSize = 4;
    private int inboundMaxPoolSize = 8;
    private int inboundQueueCapacity = 256;
    private int outboundCorePoolSize = 4;
    private int outboundMaxPoolSize = 8;
    private int outboundQueueCapacity = 256;
  }
}
