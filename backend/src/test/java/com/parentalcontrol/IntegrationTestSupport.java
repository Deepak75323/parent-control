package com.parentalcontrol;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.UUID;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

/**
 * Uses the local Podman compose Postgres/Redis stack instead of Testcontainers Ryuk (Ryuk is
 * unreliable on Podman-only macOS setups).
 */
public final class IntegrationTestSupport {

  public static final String DEFAULT_FAMILY_CHILD_PASSWORD = "TestChild1234";
  public static final String TEST_TAMPER_MASTER_PIN = "test-tamper-" + UUID.randomUUID();

  private static final String JDBC_PREFIX = "jdbc:postgresql://localhost:5432/";

  private IntegrationTestSupport() {}

  public static void configureLocalInfra(DynamicPropertyRegistry registry, String databaseName) {
    registry.add("spring.datasource.url", () -> JDBC_PREFIX + databaseName);
    registry.add("spring.datasource.username", () -> envOrFail("POSTGRES_USER", "pcp"));
    registry.add("spring.datasource.password", () -> envOrFail("POSTGRES_PASSWORD", null));
    registry.add("spring.data.redis.host", () -> "localhost");
    registry.add("spring.data.redis.port", () -> "6379");
    registry.add("app.rate-limit.enabled", () -> "false");
    registry.add("app.billing.enforcement-enabled", () -> "false");
    registry.add("app.email-verification.enforcement-enabled", () -> "false");
    registry.add("app.account-lockout.enabled", () -> "false");
    registry.add("app.jwt.secret", () -> envOrFail("JWT_SECRET", null));
    registry.add("tamper.master-pin.enabled", () -> "true");
    registry.add("tamper.master-pin.pin", () -> TEST_TAMPER_MASTER_PIN);
  }

  /** Enables RabbitMQ STOMP relay against the local Podman compose stack. */
  public static void configureStompRelay(DynamicPropertyRegistry registry) {
    registry.add("app.stomp.relay-enabled", () -> "true");
    registry.add("app.stomp.relay.host", () -> "localhost");
    registry.add("app.stomp.relay.port", () -> "61614");
    registry.add("app.stomp.relay.system-login", () -> envOrFail("RABBITMQ_USER", "pcp"));
    registry.add("app.stomp.relay.system-passcode", () -> envOrFail("RABBITMQ_PASSWORD", null));
    registry.add("app.stomp.relay.client-login", () -> envOrFail("RABBITMQ_USER", "pcp"));
    registry.add("app.stomp.relay.client-passcode", () -> envOrFail("RABBITMQ_PASSWORD", null));
  }

  public static boolean isRabbitMqStompReachable() {
    try (var socket = new java.net.Socket()) {
      socket.connect(new java.net.InetSocketAddress("localhost", 61614), 2_000);
      return true;
    } catch (Exception ignored) {
      return false;
    }
  }

  public static void setFamilyChildLoginPassword(MockMvc mockMvc, String accessToken)
      throws Exception {
    setFamilyChildLoginPassword(mockMvc, accessToken, DEFAULT_FAMILY_CHILD_PASSWORD);
  }

  public static void setFamilyChildLoginPassword(
      MockMvc mockMvc, String accessToken, String password) throws Exception {
    mockMvc
        .perform(
            post("/api/v1/parent/child-login-password")
                .header("Authorization", "Bearer " + accessToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"childLoginPassword\":\"" + password + "\"}"))
        .andExpect(status().isOk());
  }

  public static String createPairingCode(
      MockMvc mockMvc, ObjectMapper objectMapper, String accessToken) throws Exception {
    setFamilyChildLoginPassword(mockMvc, accessToken);
    MvcResult code =
        mockMvc
            .perform(
                post("/api/v1/devices/pairing-code")
                    .header("Authorization", "Bearer " + accessToken)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{}"))
            .andExpect(status().isOk())
            .andReturn();
    return objectMapper
        .readTree(code.getResponse().getContentAsString())
        .get("data")
        .get("code")
        .asText();
  }

  private static String envOrFail(String name, String fallback) {
    String value = System.getenv(name);
    if (value != null && !value.isBlank()) {
      return value;
    }
    if (fallback != null) {
      return fallback;
    }
    throw new IllegalStateException(
        name + " must be set for integration tests — copy .env.example to .env at repo root");
  }
}
