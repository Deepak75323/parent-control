package com.parentalcontrol.config;

import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;

@Configuration
@ConditionalOnProperty(prefix = "app.kafka", name = "enabled", havingValue = "true")
public class KafkaConfig {

  @Bean
  public NewTopic notificationsTopic(KafkaProperties props) {
    return TopicBuilder.name(props.getTopicNotifications()).partitions(3).replicas(1).build();
  }

  @Bean
  public NewTopic locationTopic(KafkaProperties props) {
    return TopicBuilder.name(props.getTopicLocation()).partitions(3).replicas(1).build();
  }

  @Bean
  public NewTopic usageStatsTopic(KafkaProperties props) {
    return TopicBuilder.name(props.getTopicUsageStats()).partitions(3).replicas(1).build();
  }

  @Bean
  public NewTopic heartbeatTopic(KafkaProperties props) {
    return TopicBuilder.name(props.getTopicHeartbeat()).partitions(3).replicas(1).build();
  }
}
