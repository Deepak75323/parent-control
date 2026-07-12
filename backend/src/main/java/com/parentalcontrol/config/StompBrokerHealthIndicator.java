package com.parentalcontrol.config;

import java.net.InetSocketAddress;
import java.net.Socket;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/** Readiness signal when the API cannot reach the RabbitMQ STOMP relay. */
@Component
@ConditionalOnProperty(name = "app.stomp.relay-enabled", havingValue = "true")
@RequiredArgsConstructor
public class StompBrokerHealthIndicator implements HealthIndicator {

  private final StompBrokerProperties stompBrokerProperties;

  @Override
  public Health health() {
    StompBrokerProperties.Relay relay = stompBrokerProperties.getRelay();
    try (Socket socket = new Socket()) {
      socket.connect(
          new InetSocketAddress(relay.getHost(), relay.getPort()), relay.getConnectTimeoutMs());
      return Health.up()
          .withDetail("relayHost", relay.getHost())
          .withDetail("relayPort", relay.getPort())
          .build();
    } catch (Exception e) {
      return Health.down()
          .withDetail("relayHost", relay.getHost())
          .withDetail("relayPort", relay.getPort())
          .withException(e)
          .build();
    }
  }
}
