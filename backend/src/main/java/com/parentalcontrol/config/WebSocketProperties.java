package com.parentalcontrol.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;

@Data
@ConfigurationProperties(prefix = "app.websocket")
public class WebSocketProperties {

    /** Allowed Origin patterns for /ws and /ws-native. Use * for dev only. */
    private List<String> allowedOriginPatterns = List.of("*");
}
