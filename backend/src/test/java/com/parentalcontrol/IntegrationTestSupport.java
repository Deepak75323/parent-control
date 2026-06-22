package com.parentalcontrol;

import org.springframework.test.context.DynamicPropertyRegistry;

/**
 * Uses the local Podman compose Postgres/Redis stack instead of Testcontainers Ryuk
 * (Ryuk is unreliable on Podman-only macOS setups).
 */
public final class IntegrationTestSupport {

    private static final String JDBC_PREFIX = "jdbc:postgresql://localhost:5432/";
    private static final String DB_USER = "pcp";
    private static final String DB_PASSWORD = "pcp_secret";

    private IntegrationTestSupport() {}

    public static void configureLocalInfra(DynamicPropertyRegistry registry, String databaseName) {
        registry.add("spring.datasource.url", () -> JDBC_PREFIX + databaseName);
        registry.add("spring.datasource.username", () -> DB_USER);
        registry.add("spring.datasource.password", () -> DB_PASSWORD);
        registry.add("spring.data.redis.host", () -> "localhost");
        registry.add("spring.data.redis.port", () -> "6379");
        registry.add("app.rate-limit.enabled", () -> "false");
        registry.add("app.billing.enforcement-enabled", () -> "false");
    }

    /** Enables RabbitMQ STOMP relay against the local Podman compose stack. */
    public static void configureStompRelay(DynamicPropertyRegistry registry) {
        registry.add("app.stomp.relay-enabled", () -> "true");
        registry.add("app.stomp.relay.host", () -> "localhost");
        registry.add("app.stomp.relay.port", () -> "61614");
        registry.add("app.stomp.relay.system-login", () -> "pcp");
        registry.add("app.stomp.relay.system-passcode", () -> "pcp_secret");
        registry.add("app.stomp.relay.client-login", () -> "pcp");
        registry.add("app.stomp.relay.client-passcode", () -> "pcp_secret");
    }

    public static boolean isRabbitMqStompReachable() {
        try (var socket = new java.net.Socket()) {
            socket.connect(new java.net.InetSocketAddress("localhost", 61614), 2_000);
            return true;
        } catch (Exception ignored) {
            return false;
        }
    }
}
