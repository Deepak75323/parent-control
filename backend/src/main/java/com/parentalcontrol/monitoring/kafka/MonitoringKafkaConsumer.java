package com.parentalcontrol.monitoring.kafka;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(prefix = "app.kafka", name = "enabled", havingValue = "true")
@RequiredArgsConstructor
@Slf4j
public class MonitoringKafkaConsumer {

  private final ObjectMapper objectMapper;

  @KafkaListener(topics = "${app.kafka.topic-notifications}", groupId = "pcp-monitoring-consumer")
  public void onNotifications(String raw) {
    logEvent("notifications", raw);
  }

  @KafkaListener(topics = "${app.kafka.topic-location}", groupId = "pcp-monitoring-consumer")
  public void onLocation(String raw) {
    logEvent("location", raw);
  }

  @KafkaListener(topics = "${app.kafka.topic-usage-stats}", groupId = "pcp-monitoring-consumer")
  public void onUsageStats(String raw) {
    logEvent("usage-stats", raw);
  }

  @KafkaListener(topics = "${app.kafka.topic-heartbeat}", groupId = "pcp-monitoring-consumer")
  public void onHeartbeat(String raw) {
    log.debug("Kafka heartbeat event: device partition processed");
  }

  private void logEvent(String stream, String raw) {
    try {
      MonitoringEventMessage msg = objectMapper.readValue(raw, MonitoringEventMessage.class);
      log.debug("Kafka [{}] {} device={}", stream, msg.getEventType(), msg.getDeviceId());
    } catch (Exception e) {
      log.warn("Kafka consumer parse error on {}: {}", stream, e.getMessage());
    }
  }
}
