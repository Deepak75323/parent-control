package com.parentalcontrol.config;

import java.util.List;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Data
@ConfigurationProperties(prefix = "app.websocket")
public class WebSocketProperties {

  /** Allowed Origin patterns for /ws and /ws-native. Use * for dev only. */
  private List<String> allowedOriginPatterns = List.of("*");
}
