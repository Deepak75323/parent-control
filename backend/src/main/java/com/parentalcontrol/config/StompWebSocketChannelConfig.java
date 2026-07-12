package com.parentalcontrol.config;

import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.simp.config.ChannelRegistration;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;

/** Bounded thread pools for inbound/outbound STOMP channels — backpressure under fan-out load. */
@Configuration
@RequiredArgsConstructor
public class StompWebSocketChannelConfig implements WebSocketMessageBrokerConfigurer {

  private final StompBrokerProperties stompBrokerProperties;

  @Override
  public void configureClientInboundChannel(ChannelRegistration registration) {
    StompBrokerProperties.Channel channel = stompBrokerProperties.getChannel();
    registration
        .taskExecutor()
        .corePoolSize(channel.getInboundCorePoolSize())
        .maxPoolSize(channel.getInboundMaxPoolSize())
        .queueCapacity(channel.getInboundQueueCapacity());
  }

  @Override
  public void configureClientOutboundChannel(ChannelRegistration registration) {
    StompBrokerProperties.Channel channel = stompBrokerProperties.getChannel();
    registration
        .taskExecutor()
        .corePoolSize(channel.getOutboundCorePoolSize())
        .maxPoolSize(channel.getOutboundMaxPoolSize())
        .queueCapacity(channel.getOutboundQueueCapacity());
  }
}
