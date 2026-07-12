package com.parentalcontrol.monitoring.kafka;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.parentalcontrol.config.KafkaProperties;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class MonitoringKafkaPublisher {

  private final KafkaProperties kafkaProperties;
  private final ObjectMapper objectMapper;

  @Autowired(required = false)
  private KafkaTemplate<String, String> kafkaTemplate;

  public void publish(String topic, String eventType, UUID deviceId, Map<String, Object> payload) {
    if (!kafkaProperties.isEnabled() || kafkaTemplate == null) {
      return;
    }
    try {
      MonitoringEventMessage message =
          MonitoringEventMessage.builder()
              .eventType(eventType)
              .deviceId(deviceId)
              .occurredAt(Instant.now())
              .payload(payload)
              .build();
      kafkaTemplate.send(topic, deviceId.toString(), objectMapper.writeValueAsString(message));
    } catch (Exception e) {
      log.warn("Kafka publish failed for {}: {}", eventType, e.getMessage());
    }
  }

  public void notifications(UUID deviceId, int count) {
    publish(
        kafkaProperties.getTopicNotifications(),
        "NOTIFICATIONS_INGESTED",
        deviceId,
        Map.of("accepted", count));
  }

  public void location(UUID deviceId, double lat, double lon) {
    publish(
        kafkaProperties.getTopicLocation(),
        "LOCATION_INGESTED",
        deviceId,
        Map.of("latitude", lat, "longitude", lon));
  }

  public void usageStats(UUID deviceId, int count) {
    publish(
        kafkaProperties.getTopicUsageStats(),
        "USAGE_STATS_INGESTED",
        deviceId,
        Map.of("accepted", count));
  }

  public void heartbeat(UUID deviceId) {
    publish(kafkaProperties.getTopicHeartbeat(), "HEARTBEAT", deviceId, Map.of());
  }
}
