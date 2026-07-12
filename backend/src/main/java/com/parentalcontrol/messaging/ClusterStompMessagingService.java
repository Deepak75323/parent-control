package com.parentalcontrol.messaging;

import com.parentalcontrol.observability.StompObservabilityService;
import lombok.RequiredArgsConstructor;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

/**
 * Routes STOMP publishes through Redis cluster fan-out when enabled, otherwise uses the local
 * in-memory broker directly.
 */
@Service
@RequiredArgsConstructor
public class ClusterStompMessagingService {

  private final SimpMessagingTemplate messagingTemplate;
  private final com.parentalcontrol.config.StompClusterProperties stompClusterProperties;
  private final org.springframework.beans.factory.ObjectProvider<RedisStompClusterPublisher>
      redisPublisher;
  private final StompObservabilityService stompObservabilityService;

  public void convertAndSend(String destination, Object payload) {
    if (stompClusterProperties.getMode()
        == com.parentalcontrol.config.StompClusterProperties.Mode.REDIS) {
      redisPublisher.getObject().publish(destination, payload);
      stompObservabilityService.recordPublish(destination, "redis");
      stompObservabilityService.recordClusterFanout(destination);
      return;
    }
    messagingTemplate.convertAndSend(destination, payload);
    stompObservabilityService.recordPublish(destination, "local");
  }

  public void convertAndSendToUser(String userId, String destination, Object payload) {
    if (stompClusterProperties.getMode()
        == com.parentalcontrol.config.StompClusterProperties.Mode.REDIS) {
      redisPublisher.getObject().publishToUser(userId, destination, payload);
      stompObservabilityService.recordPublish(destination, "redis-user");
      stompObservabilityService.recordClusterFanout(destination);
      return;
    }
    messagingTemplate.convertAndSendToUser(userId, destination, payload);
    stompObservabilityService.recordPublish(destination, "local-user");
  }
}
