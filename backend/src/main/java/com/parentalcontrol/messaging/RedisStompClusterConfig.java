package com.parentalcontrol.messaging;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.connection.Message;
import org.springframework.data.redis.connection.MessageListener;
import org.springframework.data.redis.listener.ChannelTopic;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;
import org.springframework.messaging.simp.SimpMessagingTemplate;

/**
 * Each API replica subscribes to the Redis STOMP channel and forwards messages to
 * locally connected WebSocket clients.
 */
@Configuration
@ConditionalOnProperty(name = "app.stomp.cluster.mode", havingValue = "redis")
@RequiredArgsConstructor
@Slf4j
public class RedisStompClusterConfig {

    private final com.parentalcontrol.config.StompClusterProperties stompClusterProperties;
    private final SimpMessagingTemplate messagingTemplate;
    private final ObjectMapper objectMapper;

    @Bean
    RedisMessageListenerContainer stompClusterRedisListenerContainer(RedisConnectionFactory factory) {
        RedisMessageListenerContainer container = new RedisMessageListenerContainer();
        container.setConnectionFactory(factory);
        MessageListener listener = (Message message, byte[] pattern) ->
                onClusterMessage(new String(message.getBody()));
        container.addMessageListener(listener, new ChannelTopic(stompClusterProperties.getRedis().getChannel()));
        log.info("Redis STOMP cluster listener active on channel={}",
                stompClusterProperties.getRedis().getChannel());
        return container;
    }

    void onClusterMessage(String message) {
        try {
            StompClusterMessage envelope = objectMapper.readValue(message, StompClusterMessage.class);
            String destination = envelope.destination();
            Object payload = objectMapper.readValue(envelope.payloadJson(), Object.class);

            if (destination.startsWith("/user/")) {
                String remainder = destination.substring("/user/".length());
                int slash = remainder.indexOf('/');
                if (slash > 0) {
                    String userId = remainder.substring(0, slash);
                    String userDest = remainder.substring(slash);
                    messagingTemplate.convertAndSendToUser(userId, userDest, payload);
                    return;
                }
            }
            messagingTemplate.convertAndSend(destination, payload);
        } catch (Exception e) {
            log.error("Failed to handle Redis STOMP cluster message: {}", message, e);
        }
    }
}
