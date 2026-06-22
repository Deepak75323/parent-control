package com.parentalcontrol.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Cross-pod STOMP fan-out. {@code redis} publishes once to Redis; each API replica
 * forwards to its local WebSocket sessions via the in-memory simple broker.
 */
@Data
@ConfigurationProperties(prefix = "app.stomp.cluster")
public class StompClusterProperties {

    /** local = single replica; redis = horizontal scaling via Redis pub/sub. */
    private Mode mode = Mode.LOCAL;

    private Redis redis = new Redis();

    public enum Mode {
        LOCAL,
        REDIS
    }

    @Data
    public static class Redis {
        private String channel = "pcp:stomp:cluster";
    }
}
