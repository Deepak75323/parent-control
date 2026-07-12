package com.parentalcontrol.config;

import com.parentalcontrol.common.constant.ApiConstants;
import com.parentalcontrol.common.constant.SocketConstants;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.simp.config.ChannelRegistration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;

/**
 * STOMP WebSocket broker for real-time device status, commands, and signaling.
 *
 * <p>With {@code app.stomp.relay-enabled=true}, messages fan out through RabbitMQ so multiple API
 * replicas behave as a single logical broker.
 */
@Configuration
@EnableWebSocketMessageBroker
@RequiredArgsConstructor
@Slf4j
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

  private final StompAuthChannelInterceptor stompAuthChannelInterceptor;
  private final WebSocketProperties webSocketProperties;
  private final StompBrokerProperties stompBrokerProperties;

  @Override
  public void configureClientInboundChannel(ChannelRegistration registration) {
    registration.interceptors(stompAuthChannelInterceptor);
  }

  @Override
  public void configureMessageBroker(MessageBrokerRegistry registry) {
    if (stompBrokerProperties.isRelayEnabled()) {
      configureBrokerRelay(registry);
    } else {
      log.warn("STOMP simple in-memory broker active — safe only for single API replica");
      registry.enableSimpleBroker(SocketConstants.TOPIC_PREFIX, SocketConstants.QUEUE_PREFIX);
    }
    registry.setApplicationDestinationPrefixes(SocketConstants.APP_PREFIX);
    registry.setUserDestinationPrefix(SocketConstants.USER_PREFIX);
  }

  private void configureBrokerRelay(MessageBrokerRegistry registry) {
    StompBrokerProperties.Relay relay = stompBrokerProperties.getRelay();
    log.info(
        "STOMP broker relay enabled — host={} port={} userRegistryBroadcast={}",
        relay.getHost(),
        relay.getPort(),
        relay.getUserRegistryBroadcast());
    registry
        .enableStompBrokerRelay(SocketConstants.TOPIC_PREFIX, SocketConstants.QUEUE_PREFIX)
        .setRelayHost(relay.getHost())
        .setRelayPort(relay.getPort())
        .setSystemLogin(relay.getSystemLogin())
        .setSystemPasscode(relay.getSystemPasscode())
        .setClientLogin(relay.getClientLogin())
        .setClientPasscode(relay.getClientPasscode())
        .setUserDestinationBroadcast(relay.getUserDestinationBroadcast())
        .setUserRegistryBroadcast(relay.getUserRegistryBroadcast())
        .setSystemHeartbeatSendInterval(relay.getHeartbeatSendIntervalMs())
        .setSystemHeartbeatReceiveInterval(relay.getHeartbeatReceiveIntervalMs());
  }

  @Override
  public void registerStompEndpoints(StompEndpointRegistry registry) {
    String[] origins = webSocketProperties.getAllowedOriginPatterns().toArray(String[]::new);
    registry.addEndpoint(ApiConstants.WS_NATIVE).setAllowedOriginPatterns(origins);
    registry.addEndpoint(ApiConstants.WS).setAllowedOriginPatterns(origins).withSockJS();
  }
}
