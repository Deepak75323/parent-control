package com.parentalcontrol.messaging;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "app.stomp.cluster.mode", havingValue = "redis")
@RequiredArgsConstructor
@Slf4j
public class RedisStompClusterPublisher {

    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;
    private final com.parentalcontrol.config.StompClusterProperties stompClusterProperties;

    public void publish(String destination, Object payload) {
        try {
            String payloadJson = objectMapper.writeValueAsString(payload);
            String body = objectMapper.writeValueAsString(new StompClusterMessage(destination, payloadJson));
            redisTemplate.convertAndSend(stompClusterProperties.getRedis().getChannel(), body);
        } catch (Exception e) {
            log.error("Failed to publish STOMP cluster message destination={}", destination, e);
            throw new IllegalStateException("STOMP cluster publish failed", e);
        }
    }

    public void publishToUser(String userId, String destination, Object payload) {
        publish("/user/" + userId + destination, payload);
    }
}
