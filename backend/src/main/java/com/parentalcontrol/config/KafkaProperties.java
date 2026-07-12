package com.parentalcontrol.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "app.kafka")
public class KafkaProperties {

  private boolean enabled = false;

  private String topicNotifications = "pcp.monitoring.notifications";

  private String topicLocation = "pcp.monitoring.location";

  private String topicUsageStats = "pcp.monitoring.usage-stats";

  private String topicHeartbeat = "pcp.monitoring.heartbeat";
}
